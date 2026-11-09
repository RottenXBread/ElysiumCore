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

/** {@code /pay <player> <amount>} - transfers money to an online player. */
public class PayCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economy;
    private final Messages messages;

    public PayCommand(EconomyManager economy, Messages messages) {
        this.economy = economy;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (args.length != 2) {
            messages.send(player, "usage-pay");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(player, "player-not-found", "player", args[0]);
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messages.send(player, "pay-self");
            return true;
        }

        BigDecimal amount = economy.parseAmount(args[1]);
        if (amount == null || amount.signum() <= 0) {
            messages.send(player, "invalid-amount");
            return true;
        }

        economy.ensureAccount(player);
        economy.ensureAccount(target);

        EconomyManager.TransferResult result = economy.transfer(player.getUniqueId(), target.getUniqueId(), amount);
        switch (result) {
            case INSUFFICIENT_FUNDS -> messages.send(player, "pay-insufficient",
                    "amount", economy.format(amount));
            case BALANCE_LIMIT -> messages.send(player, "balance-limit",
                    "max", economy.format(economy.getMaxBalance()));
            case SUCCESS -> {
                String formatted = economy.format(amount);
                messages.send(player, "pay-sent", "player", target.getName(), "amount", formatted);
                messages.send(target, "pay-received", "player", player.getName(), "amount", formatted);
            }
        }
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
