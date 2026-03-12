package fr.townyconflict.managers;

import fr.townyconflict.TownyConflict;
import java.util.HashMap;
import java.util.Map;

public class ReputationManager {

    private final TownyConflict plugin;
    private final Map<String, Integer> reputations = new HashMap<>();

    public ReputationManager(TownyConflict plugin) {
        this.plugin = plugin;
        loadFromDatabase();
    }

    public int getReputation(String townName) {
        return reputations.getOrDefault(townName.toLowerCase(), 0);
    }

    public void addReputation(String townName, int amount) {
        if (!plugin.getConfigManager().isReputationEnabled()) return;
        int current = getReputation(townName);
        reputations.put(townName.toLowerCase(), current + amount);
        plugin.getDatabaseManager().saveReputation(townName, current + amount);
    }

    public void setReputation(String townName, int value) {
        reputations.put(townName.toLowerCase(), value);
        plugin.getDatabaseManager().saveReputation(townName, value);
    }

    public String getReputationLabel(int rep) {
        if (rep >= 50) return "§aExcellente";
        if (rep >= 20) return "§aBonne";
        if (rep >= 0) return "§eNeutre";
        if (rep >= -20) return "§6Mauvaise";
        if (rep >= -50) return "§cTrès mauvaise";
        return "§4Infâme";
    }

    private void loadFromDatabase() {
        reputations.putAll(plugin.getDatabaseManager().loadReputations());
    }
}
