package com.elysium.core.teleport;

import com.elysium.core.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /tpaccept} - accepts the most recent incoming teleport request. */
public class TpAcceptCommand implements CommandExecutor {

    private final TeleportManager teleport;
    private final Messages messages;

    public TpAcceptCommand(TeleportManager teleport, Messages messages) {
        this.teleport = teleport;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        teleport.accept(player);
        return true;
    }
}
