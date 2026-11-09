package com.elysium.core.admin;

import com.elysium.core.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /vanish} (alias {@code /v}) - toggles vanish. Permission {@code elysium.admin.vanish}. */
public class VanishCommand implements CommandExecutor {

    private final VanishManager vanishManager;
    private final Messages messages;

    public VanishCommand(VanishManager vanishManager, Messages messages) {
        this.vanishManager = vanishManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("elysium.admin.vanish")) {
            messages.send(player, "no-permission");
            return true;
        }
        // The message is sent by the manager so the toggle stays in one place.
        vanishManager.setVanished(player, !vanishManager.isVanished(player.getUniqueId()));
        return true;
    }
}
