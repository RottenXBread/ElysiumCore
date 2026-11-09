package com.elysium.core.admin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Proxies interactions with {@link PlayerInventoryView}s.
 *
 * <p>Locked filler slots reject clicks and drags; every other interaction inside the view
 * is allowed and schedules a one-tick flush so Bukkit has finished applying it first.
 * That covers normal clicks, shift-clicks, drops, swaps and drags across mapped slots.</p>
 */
public class InventorySeeListener implements Listener {

    private final InventorySeeManager inventorySee;

    public InventorySeeListener(InventorySeeManager inventorySee) {
        this.inventorySee = inventorySee;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        // getView().getTopInventory() is reliable even for clicks in the player's own part.
        if (!(event.getView().getTopInventory().getHolder() instanceof PlayerInventoryView view)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < PlayerInventoryView.SIZE && view.isLocked(rawSlot)) {
            // Locked filler slots can never be picked up, placed into or swapped.
            event.setCancelled(true);
            return;
        }
        // Anything else is legitimate item movement; flush to the target next tick.
        view.scheduleSync();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PlayerInventoryView view)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < PlayerInventoryView.SIZE && view.isLocked(rawSlot)) {
                // Dragging across a locked slot is rejected as a whole.
                event.setCancelled(true);
                return;
            }
        }
        view.scheduleSync();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onClose(InventoryCloseEvent event) {
        inventorySee.handleClose(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        inventorySee.handleQuit(event.getPlayer());
    }
}
