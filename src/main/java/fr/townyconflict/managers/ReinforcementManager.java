package fr.townyconflict.managers;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import fr.townyconflict.TownyConflict;
import fr.townyconflict.models.Assault;
import fr.townyconflict.models.War;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReinforcementManager {

    private final TownyConflict plugin;
    // assaultId -> (townSide -> count actuel de renforts)
    private final Map<UUID, Map<String, Integer>> reinforcementCounts = new HashMap<>();

    public ReinforcementManager(TownyConflict plugin) {
        this.plugin = plugin;
    }

    public enum JoinResult {
        SUCCESS, ASSAULT_NOT_FOUND, WAR_REINFORCEMENTS_DISABLED,
        QUOTA_FULL, NOT_IN_ALLIED_NATION, ALREADY_PARTICIPATING,
        SAME_NATION_AS_OPPONENT
    }

    public JoinResult joinAsReinforcement(Player player, String defenderTownName, boolean joinAttackers) {
        Assault assault = plugin.getAssaultManager().getAssaultForTown(defenderTownName);
        if (assault == null) return JoinResult.ASSAULT_NOT_FOUND;

        War war = plugin.getWarManager().getWarBetween(assault.getAttackerTown(), assault.getDefenderTown());
        if (war == null) return JoinResult.ASSAULT_NOT_FOUND;

        // Vérif : renforts autorisés dans cette guerre
        if (!war.isAllowNationReinforcements()) return JoinResult.WAR_REINFORCEMENTS_DISABLED;

        // Vérif : déjà participant
        if (assault.isParticipant(player.getUniqueId())) return JoinResult.ALREADY_PARTICIPATING;

        // Vérif : le joueur appartient à la bonne nation
        Resident res = TownyAPI.getInstance().getResident(player.getName());
        if (res == null || !res.hasTown()) return JoinResult.NOT_IN_ALLIED_NATION;

        Town playerTown = res.getTownOrNull();
        String sideToJoin = joinAttackers ? assault.getAttackerTown() : assault.getDefenderTown();
        Town sideTown = TownyAPI.getInstance().getTown(sideToJoin);

        if (playerTown == null || sideTown == null) return JoinResult.NOT_IN_ALLIED_NATION;

        // Même town = participent directement, pas comme renforts
        if (playerTown.getName().equalsIgnoreCase(sideToJoin)) return JoinResult.ALREADY_PARTICIPATING;

        // Vérif : même nation que le camp à rejoindre
        Nation playerNation = playerTown.hasNation() ? playerTown.getNationOrNull() : null;
        Nation sideNation = sideTown.hasNation() ? sideTown.getNationOrNull() : null;
        if (playerNation == null || sideNation == null ||
                !playerNation.getName().equalsIgnoreCase(sideNation.getName())) {
            return JoinResult.NOT_IN_ALLIED_NATION;
        }

        // Vérif : quota
        int quota = plugin.getConfigManager().getReinforcementQuota(sideNation.getTowns().size());
        Map<String, Integer> counts = reinforcementCounts.computeIfAbsent(assault.getId(), k -> new HashMap<>());
        int current = counts.getOrDefault(sideToJoin.toLowerCase(), 0);
        if (current >= quota) return JoinResult.QUOTA_FULL;

        // Ajout du renfort
        counts.put(sideToJoin.toLowerCase(), current + 1);
        assault.addReinforcement(player.getUniqueId());
        if (joinAttackers) assault.addAttacker(player.getUniqueId());
        else assault.addDefender(player.getUniqueId());

        // Téléportation
        if (joinAttackers && assault.getRallyPoint() != null) {
            player.teleport(assault.getRallyPoint());
        } else {
            Town defTown = TownyAPI.getInstance().getTown(assault.getDefenderTown());
            if (defTown != null && defTown.getSpawnOrNull() != null)
                player.teleport(defTown.getSpawnOrNull());
        }

        player.sendMessage("§aVous avez rejoint l'assaut en renfort pour §f" + sideToJoin + "§a !");
        player.sendMessage("§7Renforts utilisés : §f" + (current + 1) + "/" + quota);
        return JoinResult.SUCCESS;
    }

    public int getRemainingQuota(Assault assault, String townName) {
        Town town = TownyAPI.getInstance().getTown(townName);
        if (town == null) return 0;
        Nation nation = town.hasNation() ? town.getNationOrNull() : null;
        int quota = nation != null ?
                plugin.getConfigManager().getReinforcementQuota(nation.getTowns().size()) : 0;
        Map<String, Integer> counts = reinforcementCounts.get(assault.getId());
        int used = counts != null ? counts.getOrDefault(townName.toLowerCase(), 0) : 0;
        return Math.max(0, quota - used);
    }

    public void cleanupAssault(UUID assaultId) {
        reinforcementCounts.remove(assaultId);
    }
}
