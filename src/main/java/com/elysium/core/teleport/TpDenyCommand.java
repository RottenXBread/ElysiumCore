package com.elysium.core.teleport;

import com.elysium.core.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /tpdeny} - denies the most recent incoming teleport request. */
public class TpDenyCommand implements CommandExecutor {

    private final TeleportManager teleport;
    private final Messages messages;

    public TpDenyCommand(TeleportManager teleport, Messages messages) {
        this.teleport = teleport;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        teleport.deny(player);
        return true;
    }
}
