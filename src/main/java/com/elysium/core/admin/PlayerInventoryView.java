package com.elysium.core.admin;

import com.elysium.core.ElysiumCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A live, editable projection of a player's inventory inside a 45-slot chest GUI.
 *
 * <p>The target's real {@link PlayerInventory} is never opened directly (that is buggy on
 * Paper/Spigot and hides armour/offhand), a custom {@link InventoryHolder} is used instead.
 * Slot mapping:</p>
 *
 * <pre>
 *   view  0..26 -> player  9..35  (main inventory)
 *   view 27..35 -> player  0..8   (hotbar)
 *   view 36..39 -> player 36..39  (armour: boots, leggings, chestplate, helmet)
 *   view 40    -> player 40       (offhand)
 *   view 41..44 -> locked filler slots
 * </pre>
 *
 * <p>{@code lastKnown} mirrors what the target's inventory looked like when the view was
 * last synchronised. {@link #flush(Player)} writes only slots whose view content differs
 * from that mirror, so concurrent changes made by the target are not overwritten.</p>
 */
public class PlayerInventoryView implements InventoryHolder {

    /** Total chest size: 45 slots. */
    public static final int SIZE = 45;
    /** First of the four locked filler slots. */
    public static final int LOCKED_START = 41;

    /** view slot -> player inventory slot, -1 for locked slots. */
    private static final int[] VIEW_TO_PLAYER = new int[SIZE];

    static {
        Arrays.fill(VIEW_TO_PLAYER, -1);
        for (int i = 0; i < 27; i++) {
            VIEW_TO_PLAYER[i] = i + 9;          // main inventory
        }
        for (int i = 0; i < 9; i++) {
            VIEW_TO_PLAYER[27 + i] = i;         // hotbar
        }
        for (int i = 0; i < 4; i++) {
            VIEW_TO_PLAYER[36 + i] = 36 + i;    // armour
        }
        VIEW_TO_PLAYER[40] = 40;                // offhand
    }

    private final ElysiumCore plugin;
    private final UUID viewerId;
    private final UUID targetId;
    private final Inventory inventory;

    /** Snapshot of the target's inventory as last shown/known, one entry per view slot. */
    private final ItemStack[] lastKnown = new ItemStack[SIZE];

    /** True while a click/drag is waiting for its one-tick flush; refresh pauses meanwhile. */
    private boolean syncScheduled;
    private boolean closed;

    public PlayerInventoryView(ElysiumCore plugin, UUID viewerId, UUID targetId, String targetName) {
        this.plugin = plugin;
        this.viewerId = viewerId;
        this.targetId = targetId;
        // Colour-coded title, e.g. §8Elysium §7» §fNotch
        String title = ChatColor.translateAlternateColorCodes('&', "&8Elysium &7\u00bb &f" + targetName);
        this.inventory = Bukkit.createInventory(this, SIZE, title);

        // The locked filler slots are pre-filled so normal items can never land there.
        for (int slot = LOCKED_START; slot < SIZE; slot++) {
            inventory.setItem(slot, createLockedItem());
        }
    }

    private static ItemStack createLockedItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // A distinct display name keeps the panes from stacking with normal grey panes.
            meta.setDisplayName(ChatColor.DARK_GRAY + "\u2716 Locked Slot");
            meta.setLore(List.of(ChatColor.GRAY + "Reserved by ElysiumCore."));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID getViewerId() {
        return viewerId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public boolean isClosed() {
        return closed;
    }

    public void markClosed() {
        this.closed = true;
    }

    public boolean isSyncScheduled() {
        return syncScheduled;
    }

    public boolean isLocked(int viewSlot) {
        return viewSlot >= LOCKED_START && viewSlot < SIZE;
    }

    /** @return the player inventory slot for a view slot, or -1 for locked/unmapped slots */
    public int playerSlotFor(int viewSlot) {
        if (viewSlot < 0 || viewSlot >= SIZE) {
            return -1;
        }
        return VIEW_TO_PLAYER[viewSlot];
    }

    /**
     * Copies the target's current inventory into the view, but only for slots that really
     * changed and only while no local edit is waiting to be flushed.
     */
    public void refresh(Player target) {
        if (closed || syncScheduled || target == null) {
            return;
        }
        PlayerInventory source = target.getInventory();
        for (int viewSlot = 0; viewSlot < SIZE; viewSlot++) {
            int playerSlot = playerSlotFor(viewSlot);
            if (playerSlot < 0) {
                continue;
            }
            ItemStack live = source.getItem(playerSlot);
            if (sameItem(live, lastKnown[viewSlot])) {
                continue;
            }
            ItemStack copy = live == null ? null : live.clone();
            inventory.setItem(viewSlot, copy == null ? null : copy.clone());
            lastKnown[viewSlot] = copy;
        }
    }

    /**
     * Writes every slot the admin changed (view content differs from the last known target
     * content) back into the target's inventory, then updates the mirror.
     */
    public void flush(Player target) {
        if (target == null) {
            return;
        }
        PlayerInventory destination = target.getInventory();
        for (int viewSlot = 0; viewSlot < SIZE; viewSlot++) {
            int playerSlot = playerSlotFor(viewSlot);
            if (playerSlot < 0) {
                continue;
            }
            ItemStack current = inventory.getItem(viewSlot);
            if (sameItem(current, lastKnown[viewSlot])) {
                continue;
            }
            ItemStack copy = current == null ? null : current.clone();
            destination.setItem(playerSlot, copy == null ? null : copy.clone());
            lastKnown[viewSlot] = copy;
        }
    }

    /**
     * Schedules a one-tick flush after a click or drag. Until the flush runs, the periodic
     * refresh leaves the view alone so the admin's change is never overwritten first.
     */
    public void scheduleSync() {
        if (syncScheduled || closed) {
            return;
        }
        syncScheduled = true;
        // Run one tick later, after Bukkit/Spigot has applied the click or drag to the view.
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncScheduled = false;
            if (closed) {
                return;
            }
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                flush(target);
            }
        });
    }

    /** Null-safe item comparison including stack size. */
    private static boolean sameItem(ItemStack first, ItemStack second) {
        boolean firstEmpty = first == null || first.getType().isAir();
        boolean secondEmpty = second == null || second.getType().isAir();
        if (firstEmpty || secondEmpty) {
            return firstEmpty && secondEmpty;
        }
        return first.getAmount() == second.getAmount() && first.isSimilar(second);
    }
}
