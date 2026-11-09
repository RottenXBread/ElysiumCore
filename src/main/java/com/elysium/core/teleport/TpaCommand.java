package com.elysium.core.teleport;

import com.elysium.core.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** {@code /tpa <player>} - asks another online player for permission to teleport to them. */
public class TpaCommand implements CommandExecutor, TabCompleter {

    private final TeleportManager teleport;
    private final Messages messages;

    public TpaCommand(TeleportManager teleport, Messages messages) {
        this.teleport = teleport;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (args.length != 1) {
            messages.send(player, "usage-tpa");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(player, "player-not-found", "player", args[0]);
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messages.send(player, "tpa-self");
            return true;
        }

        teleport.request(player, target);
        messages.send(player, "tpa-sent", "player", target.getName());
        messages.send(target, "tpa-received", "player", player.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player self && online.getUniqueId().equals(self.getUniqueId())) {
                    continue;
                }
                if (online.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(online.getName());
                }
            }
        }
        return completions;
    }
}
