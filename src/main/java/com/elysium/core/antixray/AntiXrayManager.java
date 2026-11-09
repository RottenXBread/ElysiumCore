package com.elysium.core.antixray;

import com.elysium.core.ElysiumCore;
import com.elysium.core.util.DataStore;
import com.elysium.core.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Passive anti-Xray heuristics: counts diamond-ore breaks in a rolling window and turns
 * ore to stone once a player exceeds the threshold.
 *
 * <p>Punishments are keyed by UUID, last for {@code anti-xray.punishment-seconds} and are
 * persisted under {@code anti-xray-punishments.<uuid>} so a restart does not reset them.
 * While punished every diamond-ore break is cancelled and converted to stone - no kick,
 * no gamemode change, no item drop.</p>
 */
public class AntiXrayManager {

    private final ElysiumCore plugin;
    private final DataStore dataStore;
    private final Messages messages;

    private final int maxDiamondOre;
    private final long windowMillis;
    private final long punishmentMillis;
    private final String alertTemplate;

    /** Rolling break timestamps (epoch millis) per player. Cleared on quit. */
    private final Map<UUID, Deque<Long>> breakHistory = new HashMap<>();
    /** Active punishment expiry (epoch millis) per player. Survives restarts via data.yml. */
    private final Map<UUID, Long> punishments = new HashMap<>();

    public AntiXrayManager(ElysiumCore plugin, DataStore dataStore, Messages messages) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.messages = messages;

        this.maxDiamondOre = Math.max(1, plugin.getConfig().getInt("anti-xray.max-diamond-ore", 15));
        long windowSeconds = Math.max(1L, plugin.getConfig().getLong("anti-xray.window-seconds", 60L));
        long punishmentSeconds = Math.max(1L, plugin.getConfig().getLong("anti-xray.punishment-seconds", 300L));
        this.windowMillis = windowSeconds * 1000L;
        this.punishmentMillis = punishmentSeconds * 1000L;
        this.alertTemplate = plugin.getConfig().getString("anti-xray.alert",
                "&c[ElysiumAlert] Player %player% is mining diamonds too quickly! X-Ray suspected.");
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void load() {
        punishments.clear();
        ConfigurationSection section = dataStore.getData().getConfigurationSection("anti-xray-punishments");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long expiry = section.getLong(key);
                if (expiry > System.currentTimeMillis()) {
                    punishments.put(uuid, expiry);
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid anti-xray punishment entry: " + key);
            }
        }
    }

    public void save() {
        Map<String, Object> stored = new HashMap<>();
        for (Map.Entry<UUID, Long> entry : punishments.entrySet()) {
            stored.put(entry.getKey().toString(), entry.getValue());
        }
        dataStore.setSection("anti-xray-punishments", stored);
        dataStore.markDirty();
    }

    // ------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------

    /** Called for every diamond-ore break (cancelled events and creative/spectator are filtered by the listener). */
    public void handleBreak(Player player, Block block, BlockBreakEvent event) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        // Already punished: every diamond-ore break becomes stone until the punishment expires.
        Long expiry = punishments.get(uuid);
        if (expiry != null && expiry > now) {
            event.setCancelled(true);
            convertToStone(player, block);
            return;
        }
        if (expiry != null) {
            punishments.remove(uuid);
            dataStore.getData().set("anti-xray-punishments." + uuid, null);
            dataStore.markDirty();
        }

        Deque<Long> history = breakHistory.computeIfAbsent(uuid, key -> new ArrayDeque<>());
        long cutoff = now - windowMillis;
        while (!history.isEmpty() && history.peekFirst() < cutoff) {
            history.pollFirst();
        }
        history.addLast(now);

        if (history.size() > maxDiamondOre) {
            punish(player, block, event, now);
        }
    }

    /** Applies the punishment: cancel the break, convert the ore, persist the expiry, alert staff. */
    private void punish(Player player, Block block, BlockBreakEvent event, long now) {
        event.setCancelled(true);
        convertToStone(player, block);

        long expiry = now + punishmentMillis;
        punishments.put(player.getUniqueId(), expiry);
        dataStore.getData().set("anti-xray-punishments." + player.getUniqueId(), expiry);
        dataStore.markDirty();

        broadcastAlert(player.getName());
    }

    /** Replaces the ore with plain stone (no physics update, no diamond drops). */
    private void convertToStone(Player player, Block block) {
        block.setType(Material.STONE, false);
        // The stone break sound makes the conversion feel like a normal (failed) break.
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);

        // Cancelling the event makes the server send the original block back to the client,
        // so re-send the stone one tick later - the ore must not reappear for the miner.
        org.bukkit.Location location = block.getLocation();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.isEnabled()) {
                player.sendBlockChange(location, Material.STONE.createBlockData());
            }
        });
    }

    /**
     * Sends the exact coloured alert to staff with {@code elysium.admin.alerts} and a
     * colour-stripped copy to the console.
     */
    private void broadcastAlert(String playerName) {
        String alert = messages.colorize(alertTemplate.replace("%player%", playerName));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("elysium.admin.alerts")) {
                online.sendMessage(alert);
            }
        }
        // Console gets the same line without colour codes.
        plugin.getLogger().warning(ChatColor.stripColor(alert));
    }

    // ------------------------------------------------------------------
    // Housekeeping
    // ------------------------------------------------------------------

    /** Removes the rolling history of a player who left; the punishment itself remains. */
    public void onQuit(Player player) {
        breakHistory.remove(player.getUniqueId());
    }

    /** Runs every 30 seconds: drops expired punishments and stale timestamps. */
    public void cleanup() {
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Long>> iterator = punishments.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() <= now) {
                iterator.remove();
                dataStore.getData().set("anti-xray-punishments." + entry.getKey(), null);
                dataStore.markDirty();
            }
        }

        long cutoff = now - windowMillis;
        List<UUID> emptyHistories = new ArrayList<>();
        for (Map.Entry<UUID, Deque<Long>> entry : breakHistory.entrySet()) {
            Deque<Long> history = entry.getValue();
            while (!history.isEmpty() && history.peekFirst() < cutoff) {
                history.pollFirst();
            }
            if (history.isEmpty()) {
                emptyHistories.add(entry.getKey());
            }
        }
        for (UUID uuid : emptyHistories) {
            breakHistory.remove(uuid);
        }
    }

    /** True while the player is serving an anti-Xray punishment. */
    public boolean isPunished(UUID uuid) {
        Long expiry = punishments.get(uuid);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    public int getMaxDiamondOre() {
        return maxDiamondOre;
    }
}
