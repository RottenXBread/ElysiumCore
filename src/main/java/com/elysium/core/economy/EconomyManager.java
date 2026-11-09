package com.elysium.core.economy;

import com.elysium.core.ElysiumCore;
import com.elysium.core.util.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Owns every balance in the plugin.
 *
 * <p>Money is always a {@link BigDecimal} rounded to two decimals with
 * {@link RoundingMode#HALF_UP}; floating point is never used for arithmetic.
 * Balances are keyed by UUID and persisted as plain strings under
 * {@code balances.<uuid>} so no precision is lost in YAML.</p>
 *
 * <p>Last known names are kept under {@code last-known-names.<uuid>} so {@code /eco}
 * can target players who are offline.</p>
 */
public class EconomyManager {

    /** Result of a transfer, mapped to a user-facing message by the command layer. */
    public enum TransferResult {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        BALANCE_LIMIT
    }

    /** Resolved target of an admin command: UUID, display name and current online state. */
    public record Target(UUID uuid, String name, boolean online) {
    }

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final ElysiumCore plugin;
    private final DataStore dataStore;

    private final Map<UUID, BigDecimal> balances = new HashMap<>();
    private final Map<UUID, String> lastKnownNames = new HashMap<>();

    private final BigDecimal startingBalance;
    private final BigDecimal maxBalance;
    private final String currencySymbol;

    public EconomyManager(ElysiumCore plugin, DataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;

        this.startingBalance = readDecimal("economy.starting-balance", "500.00");
        this.maxBalance = readDecimal("economy.max-balance", "1000000000000.00");
        this.currencySymbol = plugin.getConfig().getString("economy.currency-symbol", "$");
    }

    private BigDecimal readDecimal(String path, String fallback) {
        try {
            return new BigDecimal(plugin.getConfig().getString(path, fallback)).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            plugin.getLogger().warning("Invalid number at config path '" + path + "', using " + fallback + ".");
            return new BigDecimal(fallback);
        }
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /** Reads balances and last known names from data.yml. */
    public void load() {
        ConfigurationSection balancesSection = dataStore.getData().getConfigurationSection("balances");
        if (balancesSection != null) {
            for (String key : balancesSection.getKeys(false)) {
                UUID uuid = parseUuid(key);
                BigDecimal value = parseStored(balancesSection.get(key));
                if (uuid != null && value != null) {
                    balances.put(uuid, value);
                }
            }
        }

        ConfigurationSection namesSection = dataStore.getData().getConfigurationSection("last-known-names");
        if (namesSection != null) {
            for (String key : namesSection.getKeys(false)) {
                UUID uuid = parseUuid(key);
                String name = namesSection.getString(key);
                if (uuid != null && name != null && !name.isEmpty()) {
                    lastKnownNames.put(uuid, name);
                }
            }
        }
    }

    /** Writes balances and names back into data.yml (the caller decides when to flush). */
    public void save() {
        Map<String, Object> storedBalances = new HashMap<>();
        for (Map.Entry<UUID, BigDecimal> entry : balances.entrySet()) {
            storedBalances.put(entry.getKey().toString(), entry.getValue().toPlainString());
        }
        dataStore.setSection("balances", storedBalances);

        Map<String, Object> storedNames = new HashMap<>();
        for (Map.Entry<UUID, String> entry : lastKnownNames.entrySet()) {
            storedNames.put(entry.getKey().toString(), entry.getValue());
        }
        dataStore.setSection("last-known-names", storedNames);
        dataStore.markDirty();
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static BigDecimal parseStored(Object value) {
        if (value == null) {
            return null;
        }
        try {
            // YAML may hand back a Number (if the file was edited by hand) or a String.
            return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Accounts
    // ------------------------------------------------------------------

    public boolean hasAccount(UUID uuid) {
        return balances.containsKey(uuid);
    }

    public boolean hasAccount(Player player) {
        return player != null && hasAccount(player.getUniqueId());
    }

    /** Creates the account with the configured starting balance if it does not exist yet. */
    public void createAccount(UUID uuid, String name) {
        if (!balances.containsKey(uuid)) {
            balances.put(uuid, startingBalance);
            dataStore.markDirty();
        }
        setLastKnownName(uuid, name);
    }

    public void createAccountIfAbsent(UUID uuid, String name) {
        if (uuid == null) {
            return;
        }
        if (!balances.containsKey(uuid)) {
            balances.put(uuid, startingBalance);
            dataStore.markDirty();
        }
        if (name != null && !name.isEmpty()) {
            setLastKnownName(uuid, name);
        }
    }

    /** Ensures an account exists for a player and refreshes their last known name. */
    public void ensureAccount(Player player) {
        if (player != null) {
            createAccountIfAbsent(player.getUniqueId(), player.getName());
        }
    }

    public BigDecimal getBalance(UUID uuid) {
        BigDecimal value = balances.get(uuid);
        return value == null ? ZERO : value;
    }

    public BigDecimal getStartingBalance() {
        return startingBalance;
    }

    public BigDecimal getMaxBalance() {
        return maxBalance;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setLastKnownName(UUID uuid, String name) {
        if (uuid == null || name == null || name.isEmpty()) {
            return;
        }
        String previous = lastKnownNames.put(uuid, name);
        if (!name.equals(previous)) {
            dataStore.markDirty();
        }
    }

    public String getLastKnownName(UUID uuid) {
        return lastKnownNames.get(uuid);
    }

    /** Every name the plugin has ever seen, for tab completion. */
    public java.util.Collection<String> getKnownNames() {
        return new java.util.LinkedHashSet<>(lastKnownNames.values());
    }

    /** Case-insensitive lookup over every name the plugin has ever seen. */
    public UUID findKnownUuid(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<UUID, String> entry : lastKnownNames.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Mutation
    // ------------------------------------------------------------------

    /**
     * Adds money.
     *
     * @return false when the resulting balance would exceed the configured maximum
     */
    public boolean deposit(UUID uuid, BigDecimal amount) {
        BigDecimal next = getBalance(uuid).add(amount).setScale(2, RoundingMode.HALF_UP);
        if (next.compareTo(maxBalance) > 0) {
            return false;
        }
        balances.put(uuid, next);
        dataStore.markDirty();
        return true;
    }

    /**
     * Removes money.
     *
     * @return false when the account does not hold enough
     */
    public boolean withdraw(UUID uuid, BigDecimal amount) {
        BigDecimal current = getBalance(uuid);
        if (current.compareTo(amount) < 0) {
            return false;
        }
        BigDecimal next = current.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        balances.put(uuid, next);
        dataStore.markDirty();
        return true;
    }

    /**
     * Overwrites a balance.
     *
     * @return false when the requested balance is above the maximum
     */
    public boolean setBalance(UUID uuid, BigDecimal amount) {
        BigDecimal next = amount.setScale(2, RoundingMode.HALF_UP);
        if (next.compareTo(maxBalance) > 0) {
            return false;
        }
        balances.put(uuid, next);
        dataStore.markDirty();
        return true;
    }

    /**
     * Moves money between two accounts after validating both ends.
     * Validation happens before any mutation, so a failed transfer changes nothing.
     */
    public TransferResult transfer(UUID from, UUID to, BigDecimal amount) {
        if (getBalance(from).compareTo(amount) < 0) {
            return TransferResult.INSUFFICIENT_FUNDS;
        }
        if (getBalance(to).add(amount).compareTo(maxBalance) > 0) {
            return TransferResult.BALANCE_LIMIT;
        }
        balances.put(from, getBalance(from).subtract(amount).setScale(2, RoundingMode.HALF_UP));
        balances.put(to, getBalance(to).add(amount).setScale(2, RoundingMode.HALF_UP));
        dataStore.markDirty();
        return TransferResult.SUCCESS;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Parses user input into money. Returns null for blank, malformed or negative input.
     * Thousands separators are tolerated; values are rounded HALF_UP to two decimals.
     */
    public BigDecimal parseAmount(String input) {
        if (input == null) {
            return null;
        }
        String cleaned = input.trim().replace(",", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(cleaned);
            if (value.signum() < 0) {
                return null;
            }
            return value.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** Formats money with grouping and exactly two decimals, e.g. {@code $1,234.56}. */
    public String format(BigDecimal amount) {
        BigDecimal value = (amount == null ? ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return currencySymbol + format.format(value);
    }

    /**
     * Resolves an {@code /eco} target using, in order:
     * <ol>
     *   <li>an exact online player name,</li>
     *   <li>a known name (case-insensitive, works offline),</li>
     *   <li>any player that has played on the server before.</li>
     * </ol>
     * Returns null when nothing matched.
     */
    public Target resolveTarget(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            ensureAccount(online);
            return new Target(online.getUniqueId(), online.getName(), true);
        }

        UUID known = findKnownUuid(name);
        if (known != null) {
            Player player = Bukkit.getPlayer(known);
            if (player != null) {
                ensureAccount(player);
                return new Target(known, player.getName(), true);
            }
            String stored = getLastKnownName(known);
            createAccountIfAbsent(known, stored);
            return new Target(known, stored == null ? name : stored, false);
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            String displayName = offline.getName() == null ? name : offline.getName();
            createAccountIfAbsent(offline.getUniqueId(), displayName);
            return new Target(offline.getUniqueId(), displayName, offline.isOnline());
        }
        return null;
    }
}
