package com.elysium.core.admin;

import com.elysium.core.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** {@code /invsee <player>} - opens a live, editable view of another player's inventory. */
public class InvseeCommand implements CommandExecutor, TabCompleter {

    private final InventorySeeManager inventorySee;
    private final Messages messages;

    public InvseeCommand(InventorySeeManager inventorySee, Messages messages) {
        this.inventorySee = inventorySee;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("elysium.admin.invsee")) {
            messages.send(player, "no-permission");
            return true;
        }
        if (args.length != 1) {
            messages.send(player, "usage-invsee");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(player, "invsee-target-offline");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messages.send(player, "invsee-self");
            return true;
        }

        inventorySee.open(player, target);
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
