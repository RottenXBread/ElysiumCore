package com.elysium.core.admin;

import com.elysium.core.ElysiumCore;
import com.elysium.core.util.DataStore;
import com.elysium.core.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Vanished admins are hidden from everybody else with {@code hidePlayer}, which removes
 * both the entity and the tab list entry. Fake join/leave lines keep chat looking vanilla.
 *
 * <p>The vanished UUID set is persisted under {@code vanished} in data.yml. On plugin
 * disable visibility is restored but the set is kept, so a restart puts the same admins
 * back into vanish once the plugin enables again.</p>
 */
public class VanishManager {

    private final ElysiumCore plugin;
    private final DataStore dataStore;
    private final Messages messages;

    private final Set<UUID> vanished = new HashSet<>();

    private final String fakeLeaveMessage;
    private final String fakeJoinMessage;

    public VanishManager(ElysiumCore plugin, DataStore dataStore, Messages messages) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.messages = messages;
        this.fakeLeaveMessage = plugin.getConfig().getString("vanish.fake-leave-message", "&e%player% left the game");
        this.fakeJoinMessage = plugin.getConfig().getString("vanish.fake-join-message", "&e%player% joined the game");
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void load() {
        vanished.clear();
        for (String value : dataStore.getData().getStringList("vanished")) {
            try {
                vanished.add(UUID.fromString(value));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid vanished UUID: " + value);
            }
        }
    }

    public void save() {
        List<String> ids = new ArrayList<>(vanished.size());
        for (UUID uuid : vanished) {
            ids.add(uuid.toString());
        }
        dataStore.getData().set("vanished", ids);
        dataStore.markDirty();
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    public boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    public boolean isVanished(Player player) {
        return player != null && vanished.contains(player.getUniqueId());
    }

    public Set<UUID> getVanished() {
        return java.util.Collections.unmodifiableSet(vanished);
    }

    /**
     * Turns vanish on or off for a player.
     *
     * @return the new state, or the unchanged state when it was already as requested
     */
    public boolean setVanished(Player player, boolean state) {
        boolean currently = vanished.contains(player.getUniqueId());
        if (currently == state) {
            return currently;
        }

        if (state) {
            vanished.add(player.getUniqueId());
            save();

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                    viewer.hidePlayer(plugin, player);
                }
            }
            // Nobody but the vanished admin sees a "left the game" line.
            messages.broadcastExcept(fakeLeaveMessage.replace("%player%", player.getName()), player.getUniqueId());
            messages.send(player, "vanish-enabled");
        } else {
            vanished.remove(player.getUniqueId());
            save();

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                viewer.showPlayer(plugin, player);
            }
            messages.broadcastExcept(fakeJoinMessage.replace("%player%", player.getName()), player.getUniqueId());
            messages.send(player, "vanish-disabled");
        }
        return state;
    }

    // ------------------------------------------------------------------
    // Visibility life cycle
    // ------------------------------------------------------------------

    /** Re-applies hiding after the plugin (re)starts; persisted vanish state stays untouched. */
    public void applyOnEnable() {
        for (UUID id : vanished) {
            Player vanishedPlayer = Bukkit.getPlayer(id);
            if (vanishedPlayer == null) {
                continue;
            }
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.getUniqueId().equals(id)) {
                    viewer.hidePlayer(plugin, vanishedPlayer);
                }
            }
        }
    }

    /**
     * Applies the visibility rules for a player that just joined:
     * vanished players hide the joiner, and the joiner hides every online vanished player.
     */
    public void applyJoinVisibility(Player player) {
        if (isVanished(player.getUniqueId())) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(player.getUniqueId())) {
                    other.hidePlayer(plugin, player);
                }
            }
        }

        for (UUID id : vanished) {
            if (id.equals(player.getUniqueId())) {
                continue;
            }
            Player vanishedPlayer = Bukkit.getPlayer(id);
            if (vanishedPlayer != null) {
                player.hidePlayer(plugin, vanishedPlayer);
            }
        }
    }

    /** Makes every vanished player visible again without forgetting their vanish state. */
    public void restoreVisibility() {
        for (UUID id : vanished) {
            Player vanishedPlayer = Bukkit.getPlayer(id);
            if (vanishedPlayer == null) {
                continue;
            }
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                viewer.showPlayer(plugin, vanishedPlayer);
            }
        }
    }
}
