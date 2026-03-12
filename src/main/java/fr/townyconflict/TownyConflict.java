package fr.townyconflict;

import fr.townyconflict.commands.TCCommand;
import fr.townyconflict.listeners.AssaultListener;
import fr.townyconflict.listeners.PvpListener;
import fr.townyconflict.listeners.TownyHook;
import fr.townyconflict.managers.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class TownyConflict extends JavaPlugin {

    private static TownyConflict instance;
    private Economy economy;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private WarManager warManager;
    private AssaultManager assaultManager;
    private MercenaryManager mercenaryManager;
    private ReputationManager reputationManager;
    private TreatyManager treatyManager;
    private ReinforcementManager reinforcementManager;

    @Override
    public void onEnable() {
        instance = this;

        // Config
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);

        // Vault
        if (!setupEconomy()) {
            getLogger().severe("Vault/Economy introuvable ! TownyConflict est désactivé.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Database
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initialize();

        // Managers
        this.reputationManager = new ReputationManager(this);
        this.mercenaryManager = new MercenaryManager(this);
        this.treatyManager = new TreatyManager(this);
        this.reinforcementManager = new ReinforcementManager(this);
        this.assaultManager = new AssaultManager(this);
        this.warManager = new WarManager(this);

        // Listeners
        getServer().getPluginManager().registerEvents(new PvpListener(this), this);
        getServer().getPluginManager().registerEvents(new AssaultListener(this), this);
        getServer().getPluginManager().registerEvents(new TownyHook(this), this);

        // Commands
        TCCommand tcCommand = new TCCommand(this);
        getCommand("tc").setExecutor(tcCommand);
        getCommand("tc").setTabCompleter(tcCommand);

        getLogger().info("TownyConflict v" + getDescription().getVersion() + " activé !");
    }

    @Override
    public void onDisable() {
        if (assaultManager != null) assaultManager.cancelAllAssaults();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("TownyConflict désactivé.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    // ── Reload ──
    public void reload() {
        reloadConfig();
        configManager.reload();
        getLogger().info("TownyConflict rechargé !");
    }

    // ── Getters ──
    public static TownyConflict getInstance() { return instance; }
    public Economy getEconomy() { return economy; }
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public WarManager getWarManager() { return warManager; }
    public AssaultManager getAssaultManager() { return assaultManager; }
    public MercenaryManager getMercenaryManager() { return mercenaryManager; }
    public ReputationManager getReputationManager() { return reputationManager; }
    public TreatyManager getTreatyManager() { return treatyManager; }
    public ReinforcementManager getReinforcementManager() { return reinforcementManager; }
}
