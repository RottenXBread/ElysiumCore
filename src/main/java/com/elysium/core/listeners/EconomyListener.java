package com.elysium.core.listeners;

import com.elysium.core.economy.EconomyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Registers a balance the first time a player joins (and refreshes their last known name
 * on every join so {@code /eco} keeps working for offline players).
 */
public class EconomyListener implements Listener {

    private final EconomyManager economy;

    public EconomyListener(EconomyManager economy) {
        this.economy = economy;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        economy.ensureAccount(event.getPlayer());
    }
}
