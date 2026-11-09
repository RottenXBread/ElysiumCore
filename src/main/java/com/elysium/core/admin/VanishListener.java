package com.elysium.core.admin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps visibility and join/quit messages consistent for vanished players.
 *
 * <ul>
 *   <li>A persisted-vanished player joining produces no real join message; their
 *       visibility is applied immediately instead.</li>
 *   <li>A vanished player quitting produces no real quit message.</li>
 *   <li>Every joining player hides all online vanished admins.</li>
 * </ul>
 */
public class VanishListener implements Listener {

    private final VanishManager vanishManager;

    public VanishListener(VanishManager vanishManager) {
        this.vanishManager = vanishManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (vanishManager.isVanished(player.getUniqueId())) {
            // Suppress the vanilla join line - the admin is supposed to be invisible.
            event.setJoinMessage(null);
        }
        vanishManager.applyJoinVisibility(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (vanishManager.isVanished(player.getUniqueId())) {
            event.setQuitMessage(null);
        }
    }
}
