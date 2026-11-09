package com.elysium.core.util;

import com.elysium.core.ElysiumCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Central place for everything message related.
 *
 * <p>Messages live in {@code config.yml} under {@code messages.<key>}, may contain
 * '&amp;' colour codes and any number of {@code %placeholder%} tokens that the caller
 * supplies as {@code name, value} pairs.</p>
 *
 * <p>All lookups are done against the live {@link org.bukkit.configuration.file.FileConfiguration}
 * so {@code /reload} style config refreshes are picked up without restarting the plugin.</p>
 */
public final class Messages {

    private final ElysiumCore plugin;

    public Messages(ElysiumCore plugin) {
        this.plugin = plugin;
    }

    /** Translates '&amp;' colour codes into section signs. */
    public String colorize(String input) {
        return input == null ? "" : ChatColor.translateAlternateColorCodes('&', input);
    }

    /** The formatted prefix, or an empty string when the prefix is disabled. */
    public String prefix() {
        return colorize(plugin.getConfig().getString("messages.prefix", ""));
    }

    /** Raw (uncoloured) message template for a key. */
    public String raw(String key) {
        return plugin.getConfig().getString("messages." + key, "&cMissing message: " + key);
    }

    /**
     * Formats a message, replacing {@code %name%} tokens and translating colours.
     *
     * @param key          message key below {@code messages.}
     * @param placeholders alternating placeholder name and value pairs
     */
    public String format(String key, String... placeholders) {
        String text = raw(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String value = placeholders[i + 1];
            text = text.replace("%" + placeholders[i] + "%", value == null ? "" : value);
        }
        return colorize(text);
    }

    /** Sends a prefixed message to the sender. */
    public void send(CommandSender sender, String key, String... placeholders) {
        if (sender == null) {
            return;
        }
        sender.sendMessage(prefix() + format(key, placeholders));
    }

    /** Sends an already formatted, un-prefixed message. */
    public void sendRaw(CommandSender sender, String text) {
        if (sender != null) {
            sender.sendMessage(colorize(text));
        }
    }

    /**
     * Broadcasts a vanilla-looking line (join/leave style) to everybody except one player,
     * and mirrors it to the console. Used for the fake join/leave messages of vanish.
     *
     * @param rawMessage message template, colour codes allowed
     * @param excluded   UUID that must not receive the message (the vanished admin)
     */
    public void broadcastExcept(String rawMessage, UUID excluded) {
        String message = colorize(rawMessage);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (excluded != null && online.getUniqueId().equals(excluded)) {
                continue;
            }
            online.sendMessage(message);
        }
        // Console sees it as well so the fake message is consistent with real ones.
        Bukkit.getConsoleSender().sendMessage(message);
    }

    /** Removes colour codes, e.g. for console output. */
    public static String stripColor(String input) {
        return input == null ? "" : ChatColor.stripColor(input);
    }
}
