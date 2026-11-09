package com.elysium.core.teleport;

import com.elysium.core.ElysiumCore;
import com.elysium.core.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Handles transient {@code /tpa} requests.
 *
 * <p>Requests are deliberately <b>not</b> persisted: they are short-lived state and a
 * server restart should not resurrect stale teleport offers.</p>
 *
 * <p>Each target holds at most one request - the most recent sender replaces any older
 * one. A repeating task (one second) drops expired requests and tells both sides when
 * they are still online.</p>
 */
public class TeleportManager {

    /** Immutable request; names are cached so expiry notices work even if a player logged off. */
    private record Request(UUID sender, String senderName, String targetName, long expiresAt) {
    }

    private final Messages messages;
    private final long timeoutMillis;

    /** Key: the target's UUID. Value: the most recent incoming request. */
    private final Map<UUID, Request> requests = new HashMap<>();

    public TeleportManager(ElysiumCore plugin, Messages messages) {
        this.messages = messages;
        long seconds = plugin.getConfig().getLong("teleport.request-timeout-seconds", 60L);
        this.timeoutMillis = Math.max(1L, seconds) * 1000L;
    }

    /** Stores a new request from sender to target, replacing any previous request for that target. */
    public void request(Player sender, Player target) {
        requests.put(target.getUniqueId(), new Request(
                sender.getUniqueId(),
                sender.getName(),
                target.getName(),
                System.currentTimeMillis() + timeoutMillis));
    }

    public boolean hasRequest(UUID targetId) {
        return requests.containsKey(targetId);
    }

    /**
     * Runs once per second. Every expired request is removed and, when the players are
     * still online, both are notified.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Request>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Request> entry = iterator.next();
            Request request = entry.getValue();
            if (request.expiresAt() > now) {
                continue;
            }
            iterator.remove();

            Player sender = Bukkit.getPlayer(request.sender());
            if (sender != null) {
                messages.send(sender, "tpa-expired", "player", request.targetName());
            }
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target != null) {
                messages.send(target, "tpa-expired", "player", request.senderName());
            }
        }
    }

    /** Accepts the target's pending request and teleports the requester to the target. */
    public void accept(Player target) {
        Request request = requests.remove(target.getUniqueId());
        if (request == null) {
            messages.send(target, "tpa-none");
            return;
        }
        if (request.expiresAt() <= System.currentTimeMillis()) {
            messages.send(target, "tpa-expired", "player", request.senderName());
            Player sender = Bukkit.getPlayer(request.sender());
            if (sender != null) {
                messages.send(sender, "tpa-expired", "player", target.getName());
            }
            return;
        }

        Player sender = Bukkit.getPlayer(request.sender());
        if (sender == null) {
            messages.send(target, "tpa-sender-offline");
            return;
        }

        // Teleport to the target's *current* location, not the one from request time.
        sender.teleport(target.getLocation());
        messages.send(sender, "tpa-accepted", "player", target.getName());
        messages.send(target, "tpa-accepted-target", "player", sender.getName());
    }

    /** Denies the target's pending request and notifies both sides when possible. */
    public void deny(Player target) {
        Request request = requests.remove(target.getUniqueId());
        if (request == null) {
            messages.send(target, "tpa-none");
            return;
        }
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender != null) {
            messages.send(sender, "tpa-denied", "player", target.getName());
        }
        messages.send(target, "tpa-denied-target", "player", request.senderName());
    }

    /** Drops all requests; used on disable. */
    public void clear() {
        requests.clear();
    }
}
