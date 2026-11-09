package com.elysium.core;

import com.elysium.core.admin.FlyCommand;
import com.elysium.core.admin.InventorySeeListener;
import com.elysium.core.admin.InventorySeeManager;
import com.elysium.core.admin.InvseeCommand;
import com.elysium.core.admin.VanishCommand;
import com.elysium.core.admin.VanishListener;
import com.elysium.core.admin.VanishManager;
import com.elysium.core.antixray.AntiXrayListener;
import com.elysium.core.antixray.AntiXrayManager;
import com.elysium.core.economy.BalanceCommand;
import com.elysium.core.economy.EcoCommand;
import com.elysium.core.economy.EconomyManager;
import com.elysium.core.economy.PayCommand;
import com.elysium.core.listeners.EconomyListener;
import com.elysium.core.teleport.TeleportManager;
import com.elysium.core.teleport.TpAcceptCommand;
import com.elysium.core.teleport.TpDenyCommand;
import com.elysium.core.teleport.TpaCommand;
import com.elysium.core.util.DataStore;
import com.elysium.core.util.Messages;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

/**
 * ElysiumCore entry point.
 *
 * All core server features are initialized here and registered to their respective layouts.
 */
public final class ElysiumCore extends JavaPlugin {

    private DataStore dataStore;
    private Messages messages;

    private EconomyManager economyManager;
    private TeleportManager teleportManager;
    private VanishManager vanishManager;
    private InventorySeeManager inventorySeeManager;
    private AntiXrayManager antiXrayManager;

    private BukkitTask autoSaveTask;
    private BukkitTask teleportExpiryTask;
    private BukkitTask antiXrayCleanupTask;

    @Override
    public void onEnable() {
        // Config first: managers read their settings in their constructors.
        saveDefaultConfig();
        if (!new File(getDataFolder(), "data.yml").exists()) {
            saveResource("data.yml", false);
        }

        this.dataStore = new DataStore(this, "data.yml");
        this.dataStore.load();
        this.messages = new Messages(this);

        this.economyManager = new EconomyManager(this, dataStore);
        this.economyManager.load();

        this.teleportManager = new TeleportManager(this, messages);

        this.vanishManager = new VanishManager(this, dataStore, messages);
        this.vanishManager.load();

        this.inventorySeeManager = new InventorySeeManager(this, messages);

        this.antiXrayManager = new AntiXrayManager(this, dataStore, messages);
        this.antiXrayManager.load();

        registerCommands();
        registerListeners();
        startTasks();

        // Re-hide admins that were vanished before the restart.
        vanishManager.applyOnEnable();

        // Expose ElysiumCore as a Vault economy provider when Vault is installed.
        registerVaultEconomy();

        // Flush the parsed data back into a clean structure on first start.
        dataStore.saveNow();

        getLogger().info("ElysiumCore enabled - economy, TPA, admin tools and anti-Xray are ready.");
    }

    @Override
    public void onDisable() {
        cancelTasks();

        // Close GUI views first: this flushes pending inventory edits.
        if (inventorySeeManager != null) {
            inventorySeeManager.shutdown();
        }
        if (vanishManager != null) {
            // Restore visibility but keep the vanished set for the next start.
            vanishManager.restoreVisibility();
        }
        if (teleportManager != null) {
            teleportManager.clear();
        }

        // Persist all state one last time.
        if (economyManager != null) {
            economyManager.save();
        }
        if (vanishManager != null) {
            vanishManager.save();
        }
        if (antiXrayManager != null) {
            antiXrayManager.save();
        }
        if (dataStore != null) {
            dataStore.saveNow();
        }

        getLogger().info("ElysiumCore disabled - data saved.");
    }

    // ------------------------------------------------------------------
    // Wiring
    // ------------------------------------------------------------------

    private void registerCommands() {
        register("balance", new BalanceCommand(economyManager, messages));
        register("pay", new PayCommand(economyManager, messages));
        register("eco", new EcoCommand(economyManager, messages));

        register("tpa", new TpaCommand(teleportManager, messages));
        register("tpaccept", new TpAcceptCommand(teleportManager, messages));
        register("tpdeny", new TpDenyCommand(teleportManager, messages));

        register("fly", new FlyCommand(messages));
        register("vanish", new VanishCommand(vanishManager, messages));
        register("invsee", new InvseeCommand(inventorySeeManager, messages));
    }

    /** Attaches executor and tab completer to a command from plugin.yml. */
    private void register(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '/" + name + "' is missing from plugin.yml!");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new EconomyListener(economyManager), this);
        getServer().getPluginManager().registerEvents(new VanishListener(vanishManager), this);
        getServer().getPluginManager().registerEvents(new InventorySeeListener(inventorySeeManager), this);
        getServer().getPluginManager().registerEvents(new AntiXrayListener(antiXrayManager), this);
    }

    private void startTasks() {
        // Periodic data flush - only writes when something is dirty.
        long autoSaveSeconds = getConfig().getLong("economy.auto-save-seconds", 300L);
        if (autoSaveSeconds > 0) {
            long ticks = autoSaveSeconds * 20L;
            autoSaveTask = getServer().getScheduler().runTaskTimer(this, dataStore::saveIfDirty, ticks, ticks);
        }

        // Drop expired /tpa requests once per second and notify both sides.
        teleportExpiryTask = getServer().getScheduler()
                .runTaskTimer(this, teleportManager::tick, 20L, 20L);

        // Purge expired anti-Xray punishments and stale timestamps every 30 seconds.
        antiXrayCleanupTask = getServer().getScheduler()
                .runTaskTimer(this, antiXrayManager::cleanup, 600L, 600L);
    }

    private void cancelTasks() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        if (teleportExpiryTask != null) {
            teleportExpiryTask.cancel();
            teleportExpiryTask = null;
        }
        if (antiXrayCleanupTask != null) {
            antiXrayCleanupTask.cancel();
            antiXrayCleanupTask = null;
        }
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public VanishManager getVanishManager() {
        return vanishManager;
    }

    public InventorySeeManager getInventorySeeManager() {
        return inventorySeeManager;
    }

    public AntiXrayManager getAntiXrayManager() {
        return antiXrayManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    // ------------------------------------------------------------------
    // Vault economy bridge (Reflective Implementation)
    // ------------------------------------------------------------------

    private void registerVaultEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        try {
            Class<?> vaultEconomyClass = Class.forName("com.elysium.core.economy.VaultEconomy");
            Constructor<?> constructor = vaultEconomyClass.getConstructor(
                    ElysiumCore.class, EconomyManager.class);
            Object provider = constructor.newInstance(this, economyManager);
            Class<?> economyInterface = Class.forName("net.milkbowl.vault.economy.Economy");
            getServer().getServicesManager().register(
                    Objects.requireNonNull(economyInterface), provider, this,
                    ServicePriority.Normal);
            getLogger().info("Registered ElysiumCore as a Vault economy provider.");
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException

                | IllegalAccessException | InvocationTargetException exception) {
            getLogger().warning("Vault economy registration skipped: " + exception.getMessage());
        }
    }
}
