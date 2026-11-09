package com.elysium.core.antixray;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Feeds diamond-ore breaks into the {@link AntiXrayManager}. */
public class AntiXrayListener implements Listener {

    private final AntiXrayManager antiXray;

    public AntiXrayListener(AntiXrayManager antiXray) {
        this.antiXray = antiXray;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type != Material.DIAMOND_ORE && type != Material.DEEPSLATE_DIAMOND_ORE) {
            return;
        }

        Player player = event.getPlayer();
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            // Creative/spectator mining is not a survival X-Ray signal.
            return;
        }

        antiXray.handleBreak(player, block, event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        antiXray.onQuit(event.getPlayer());
    }
}
