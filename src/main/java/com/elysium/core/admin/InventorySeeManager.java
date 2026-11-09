package com.elysium.core.admin;

import com.elysium.core.ElysiumCore;
import com.elysium.core.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns every open {@link PlayerInventoryView}.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>open a live view and lazily start the periodic refresh task,</li>
 *   <li>flush admin edits and return cursor items when a view closes,</li>
 *   <li>close views when the target leaves (or the plugin disables),</li>
 *   <li>stop the refresh task once the last view is gone.</li>
 * </ul>
 */
public class InventorySeeManager {

    private final ElysiumCore plugin;
    private final Messages messages;
    private final long refreshTicks;

    private final Map<UUID, PlayerInventoryView> viewsByViewer = new HashMap<>();
    private final Map<UUID, Set<PlayerInventoryView>> viewsByTarget = new HashMap<>();

    private BukkitTask refreshTask;

    public InventorySeeManager(ElysiumCore plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.refreshTicks = Math.max(1L, plugin.getConfig().getLong("admin.invsee-refresh-ticks", 10L));
    }

    /** Opens a live view of {@code target} for {@code viewer}. */
    public void open(Player viewer, Player target) {
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            messages.send(viewer, "invsee-self");
            return;
        }

        // A player can only have one inventory open; close a previous view first so its
        // pending edits are flushed before the new one is created.
        PlayerInventoryView previous = viewsByViewer.get(viewer.getUniqueId());
        if (previous != null) {
            viewer.closeInventory();
            closeView(previous, true);
        }

        PlayerInventoryView view = new PlayerInventoryView(
                plugin, viewer.getUniqueId(), target.getUniqueId(), target.getName());
        viewsByViewer.put(viewer.getUniqueId(), view);
        viewsByTarget.computeIfAbsent(target.getUniqueId(), key -> new LinkedHashSet<>()).add(view);

        view.refresh(target);
        viewer.openInventory(view.getInventory());
        startTaskIfNeeded();
        messages.send(viewer, "invsee-opened", "player", target.getName());
    }

    /** Handles a generic inventory close; harmless for inventories owned by other plugins. */
    public void handleClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PlayerInventoryView view) {
            closeView(view, true);
        }
    }

    /** Cleans up when a player disconnects, as viewer or as target. */
    public void handleQuit(Player player) {
        PlayerInventoryView own = viewsByViewer.get(player.getUniqueId());
        if (own != null) {
            closeView(own, true);
        }

        Set<PlayerInventoryView> asTarget = viewsByTarget.get(player.getUniqueId());
        if (asTarget != null) {
            for (PlayerInventoryView view : new ArrayList<>(asTarget)) {
                Player viewer = Bukkit.getPlayer(view.getViewerId());
                if (viewer != null) {
                    // Closing fires InventoryCloseEvent, which flushes through closeView.
                    viewer.closeInventory();
                }
                closeView(view, true);
            }
        }
    }

    /**
     * Removes a view from all bookkeeping, flushes pending edits, and hands any cursor item
     * back to the target (or drops it as a last resort - items are never deleted).
     */
    public void closeView(PlayerInventoryView view, boolean returnCursorItem) {
        if (view == null || view.isClosed()) {
            return;
        }
        view.markClosed();

        viewsByViewer.remove(view.getViewerId(), view);
        Set<PlayerInventoryView> targetViews = viewsByTarget.get(view.getTargetId());
        if (targetViews != null) {
            targetViews.remove(view);
            if (targetViews.isEmpty()) {
                viewsByTarget.remove(view.getTargetId());
            }
        }

        Player target = Bukkit.getPlayer(view.getTargetId());
        Player viewer = Bukkit.getPlayer(view.getViewerId());

        // Write back anything the admin changed while the view was open.
        if (target != null) {
            view.flush(target);
        }

        // Return the held item rather than letting it vanish with the custom inventory.
        if (returnCursorItem && viewer != null) {
            ItemStack cursor = viewer.getItemOnCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                viewer.setItemOnCursor(new ItemStack(Material.AIR));
                giveOrDrop(target != null ? target : viewer, cursor);
            }
        }

        if (viewsByViewer.isEmpty()) {
            stopTask();
        }
    }

    /** Adds an item to a player's inventory, dropping whatever does not fit. */
    private void giveOrDrop(Player destination, ItemStack stack) {
        if (destination == null || stack == null) {
            return;
        }
        Map<Integer, ItemStack> leftover = destination.getInventory().addItem(stack.clone());
        if (leftover.isEmpty() || destination.getWorld() == null) {
            return;
        }
        for (ItemStack remainder : leftover.values()) {
            destination.getWorld().dropItemNaturally(destination.getLocation(), remainder);
        }
    }

    // ------------------------------------------------------------------
    // Refresh loop
    // ------------------------------------------------------------------

    private void startTaskIfNeeded() {
        if (refreshTask != null || viewsByViewer.isEmpty()) {
            return;
        }
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, refreshTicks, refreshTicks);
    }

    private void stopTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    /** Pulls the target's live inventory into every view that is safe to update. */
    private void refreshAll() {
        if (viewsByViewer.isEmpty()) {
            stopTask();
            return;
        }

        List<PlayerInventoryView> stale = new ArrayList<>();
        for (PlayerInventoryView view : new ArrayList<>(viewsByViewer.values())) {
            Player viewer = Bukkit.getPlayer(view.getViewerId());
            Player target = Bukkit.getPlayer(view.getTargetId());
            if (viewer == null || target == null
                    || viewer.getOpenInventory().getTopInventory().getHolder() != view) {
                stale.add(view);
                continue;
            }
            // Never fight a pending click flush, and never refresh under a held cursor.
            if (view.isSyncScheduled()) {
                continue;
            }
            if (!viewer.getItemOnCursor().getType().isAir()) {
                continue;
            }
            view.refresh(target);
        }

        for (PlayerInventoryView view : stale) {
            closeView(view, false);
        }
    }

    /** Flushes and closes every view; called from {@code onDisable}. */
    public void shutdown() {
        for (PlayerInventoryView view : new ArrayList<>(viewsByViewer.values())) {
            Player viewer = Bukkit.getPlayer(view.getViewerId());
            if (viewer != null) {
                viewer.closeInventory();
            }
            closeView(view, true);
        }
        viewsByViewer.clear();
        viewsByTarget.clear();
        stopTask();
    }
}
