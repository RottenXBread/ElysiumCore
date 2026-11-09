package com.elysium.core.economy;

import com.elysium.core.ElysiumCore;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Vault-facing economy implementation that reads from and writes to ElysiumCore's
 * own {@link EconomyManager} and {@link DataStore}.
 *
 * <p>This class is loaded reflectively from {@link ElysiumCore} only when Vault is
 * present on the server, so the absence of Vault can never prevent ElysiumCore from
 * loading. It is intentionally placed in the economy package next to the manager it
 * wraps.</p>
 */
public final class VaultEconomy implements Economy {

    private final ElysiumCore plugin;
    private final EconomyManager manager;

    public VaultEconomy(ElysiumCore plugin, EconomyManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean isEnabled() {
        return manager != null;
    }

    @Override
    public String getName() {
        return "ElysiumCore";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return manager.format(BigDecimal.valueOf(amount));
    }

    @Override
    public String currencyNamePlural() {
        return "Credits";
    }

    @Override
    public String currencyNameSingular() {
        return "Credit";
    }

    // ------------------------------------------------------------------
    // Account lookup
    // ------------------------------------------------------------------

    private UUID uuidFor(String name) {
        if (name == null) {
            return null;
        }
        OfflinePlayer exact = Bukkit.getPlayerExact(name);
        if (exact != null) {
            return exact.getUniqueId();
        }
        return manager.findKnownUuid(name);
    }

    @Deprecated
    @Override
    public boolean hasAccount(String playerName) {
        UUID uuid = uuidFor(playerName);
        return uuid != null && manager.hasAccount(uuid);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player != null && hasAccount(player.getUniqueId());
    }

    @Deprecated
    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public boolean hasAccount(UUID uuid) {
        return uuid != null && manager.hasAccount(uuid);
    }

    @Override
    public boolean playerExists(String playerName) {
        return uuidFor(playerName) != null;
    }

    @Override
    public boolean playerExists(UUID uuid) {
        return uuid != null && manager.hasAccount(uuid);
    }

    @Override
    public boolean playerExists(String playerName) {
        return uuidFor(playerName) != null;
    }

    @Override
    public boolean playerExists(UUID uuid) {
        return uuid != null && manager.hasAccount(uuid);
    }

    @Override
    public boolean playerExists(Player player) {
        return player != null && manager.hasAccount(player.getUniqueId());
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        UUID uuid = uuidFor(playerName);
        if (uuid == null) {
            return false;
        }
        manager.createAccountIfAbsent(uuid, playerName);
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return player != null && createPlayerAccount(player.getName());
    }

    @Deprecated
    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    public boolean playerExists(String playerName) {
        return uuidFor(playerName) != null;
    }

    @Override
    public boolean playerExists(UUID uuid) {
        return uuid != null && manager.hasAccount(uuid);
    }

    @Override
    public boolean playerExists(Player player) {
        return player != null && manager.hasAccount(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Balance
    // ------------------------------------------------------------------

    @Deprecated
    @Override
    public double getBalance(String playerName) {
        UUID uuid = uuidFor(playerName);
        return uuid == null ? 0.0 : toDouble(manager.getBalance(uuid));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return player == null ? 0.0 : getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(UUID uuid) {
        return toDouble(manager.getBalance(uuid));
    }

    @Deprecated
    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return player == null ? 0.0 : getBalance(player.getUniqueId());
    }

    @Deprecated
    @Override
    public boolean has(String playerName, double amount) {
        UUID uuid = uuidFor(playerName);
        return uuid != null && has(uuid, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return player != null && has(player.getUniqueId(), amount);
    }

    @Deprecated
    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public boolean has(UUID uuid, double amount) {
        return manager.getBalance(uuid).compareTo(BigDecimal.valueOf(amount)) >= 0;
    }

    // ------------------------------------------------------------------
    // Transactions
    // ------------------------------------------------------------------

    @Deprecated
    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        UUID uuid = uuidFor(playerName);
        return uuid == null ? notImplemented("Unknown player") : withdraw(uuid, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return player == null ? notImplemented("Unknown player") : withdraw(player.getUniqueId(), amount);
    }

    @Deprecated
    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Deprecated
    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        UUID uuid = uuidFor(playerName);
        return uuid == null ? notImplemented("Unknown player") : deposit(uuid, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return player == null ? notImplemented("Unknown player") : deposit(player.getUniqueId(), amount);
    }

    @Deprecated
    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createTransaction(OfflinePlayer receiver, OfflinePlayer sender, double amount, String overlay) {
        return createTransaction(receiver.getUniqueId(), sender.getUniqueId(), amount, overlay);
    }

    private EconomyResponse createTransaction(UUID receiverUuid, UUID senderUuid, double amount, String overlay) {
        if (receiverUuid == null || senderUuid == null) {
            return notImplemented("Unknown player");
        }
        BigDecimal transferred = asPlainBigDecimal(amount);
        double before = toDouble(manager.getBalance(receiverUuid));
        EconomyManager.TransferResult result = manager.transfer(senderUuid, receiverUuid, transferred);
        return switch (result) {
            case SUCCESS -> successResponse(amount, toDouble(manager.getBalance(receiverUuid)));
            case INSUFFICIENT_FUNDS -> new EconomyResponse(
                    amount, before, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
            case BALANCE_LIMIT -> new EconomyResponse(
                    amount, before, EconomyResponse.ResponseType.FAILURE, "Balance limit reached");
        };
    }

    @Override
    public boolean playerExists(Player player) {
        return player != null && manager.hasAccount(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Banks (ElysiumCore has no bank concept)
    // ------------------------------------------------------------------

    @Deprecated
    @Override
    public EconomyResponse createBank(String name, String player) {
        return notImplemented("Banks are not supported by ElysiumCore");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return notImplemented("Banks are not supported by ElysiumCore");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return notImplemented("Banks are not supported by ElysiumCore");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return notImplemented("Banks are not supported by ElysiumCore");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return notImplemented("Banks are not supported by ElysiumCore");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return notImplemented("Banks are not supported by ElysiumCore");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return notImplemented("Banks are not supported by ElysiumCore");
    }

    @Deprecated
    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return notImplemented("Banks are not supported by ElysiumCore");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return notImplemented("Banks are not supported by ElysiumCore");
    }

    @Deprecated
    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return notImplemented("Banks are not supported by ElysiumCore");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return notImplemented("Banks are not supported by ElysiumCore");
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private EconomyResponse withdraw(UUID uuid, double amount) {
        if (uuid == null) {
            return notImplemented("Unknown player");
        }
        BigDecimal decrement = asPlainBigDecimal(amount);
        double balanceBefore = toDouble(manager.getBalance(uuid));
        if (manager.withdraw(uuid, decrement)) {
            return successResponse(amount, toDouble(manager.getBalance(uuid)));
        }
        return new EconomyResponse(amount, balanceBefore, EconomyResponse.ResponseType.FAILURE,
                "Insufficient funds");
    }

    private EconomyResponse deposit(UUID uuid, double amount) {
        if (uuid == null) {
            return notImplemented("Unknown player");
        }
        BigDecimal increment = asPlainBigDecimal(amount);
        double balanceBefore = toDouble(manager.getBalance(uuid));
        if (manager.deposit(uuid, increment)) {
            return successResponse(amount, toDouble(manager.getBalance(uuid)));
        }
        return new EconomyResponse(amount, balanceBefore, EconomyResponse.ResponseType.FAILURE,
                "Balance limit reached");
    }

    private static EconomyResponse successResponse(double amount, double balance) {
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private static EconomyResponse notImplemented(String reason) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, reason);
    }

    private static BigDecimal asPlainBigDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static double toDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }
}
