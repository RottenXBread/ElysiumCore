package com.elysium.core.economy;

import com.elysium.core.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /balance} (alias {@code /bal}) - shows the sender's own balance. */
public class BalanceCommand implements CommandExecutor {

    private final EconomyManager economy;
    private final Messages messages;

    public BalanceCommand(EconomyManager economy, Messages messages) {
        this.economy = economy;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        // First /balance of a new player creates the account with the starting balance.
        economy.ensureAccount(player);
        messages.send(player, "balance", "amount", economy.format(economy.getBalance(player.getUniqueId())));
        return true;
    }
}
