package com.elysium.core.economy;

import com.elysium.core.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /eco <give|take|set> <player> <amount>} - administrative balance management.
 * Works on offline players as long as their name is known (or they have played before).
 */
public class EcoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ACTIONS = List.of("give", "take", "set");

    private final EconomyManager economy;
    private final Messages messages;

    public EcoCommand(EconomyManager economy, Messages messages) {
        this.economy = economy;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // plugin.yml already guards this, but permission plugins and command blocks are
        // not always covered, so the check is repeated here.
        if (!sender.hasPermission("elysium.admin.eco")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length != 3) {
            messages.send(sender, "usage-eco");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            messages.send(sender, "usage-eco");
            return true;
        }

        EconomyManager.Target target = economy.resolveTarget(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found", "player", args[1]);
            return true;
        }

        BigDecimal amount = economy.parseAmount(args[2]);
        if (amount == null) {
            messages.send(sender, "invalid-amount");
            return true;
        }
        // Zero is meaningless for give/take, but "set 0" is a valid way to zero a balance.
        if (!action.equals("set") && amount.signum() <= 0) {
            messages.send(sender, "invalid-amount");
            return true;
        }

        switch (action) {
            case "give" -> {
                if (!economy.deposit(target.uuid(), amount)) {
                    messages.send(sender, "balance-limit", "max", economy.format(economy.getMaxBalance()));
                    return true;
                }
            }
            case "take" -> {
                if (!economy.withdraw(target.uuid(), amount)) {
                    messages.send(sender, "eco-insufficient", "player", target.name());
                    return true;
                }
            }
            case "set" -> {
                if (!economy.setBalance(target.uuid(), amount)) {
                    messages.send(sender, "balance-limit", "max", economy.format(economy.getMaxBalance()));
                    return true;
                }
            }
            default -> {
                messages.send(sender, "usage-eco");
                return true;
            }
        }

        String formattedAmount = economy.format(amount);
        String newBalance = economy.format(economy.getBalance(target.uuid()));
        switch (action) {
            case "give" -> messages.send(sender, "eco-give", "player", target.name(),
                    "amount", formattedAmount, "balance", newBalance);
            case "take" -> messages.send(sender, "eco-take", "player", target.name(),
                    "amount", formattedAmount, "balance", newBalance);
            default -> messages.send(sender, "eco-set", "player", target.name(),
                    "amount", formattedAmount, "balance", newBalance);
        }

        // Let the target see their new balance immediately when they are online.
        Player onlineTarget = Bukkit.getPlayer(target.uuid());
        if (onlineTarget != null) {
            messages.send(onlineTarget, "balance", "amount", newBalance);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String action : ACTIONS) {
                if (action.startsWith(prefix)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 2) {
            // Online players plus every known name, so offline players can be completed too.
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    completions.add(online.getName());
                }
            }
            for (String name : economy.getKnownNames()) {
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)
                        && completions.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) {
                    completions.add(name);
                }
            }
        }
        return completions;
    }
}
