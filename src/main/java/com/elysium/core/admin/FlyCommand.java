package com.elysium.core.admin;

import com.elysium.core.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /fly} - toggles flight for the sender. Permission {@code elysium.admin.fly}. */
public class FlyCommand implements CommandExecutor {

    private final Messages messages;

    public FlyCommand(Messages messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("elysium.admin.fly")) {
            messages.send(player, "no-permission");
            return true;
        }

        boolean enable = !player.getAllowFlight();
        player.setAllowFlight(enable);
        if (!enable && player.isFlying()) {
            // Turn flight off as well, otherwise the player keeps hovering until they land.
            player.setFlying(false);
        }
        messages.send(player, enable ? "fly-enabled" : "fly-disabled");
        return true;
    }
}
