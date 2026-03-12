package fr.townyconflict.managers;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import fr.townyconflict.TownyConflict;
import fr.townyconflict.models.War;
import fr.townyconflict.models.WarReward;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.stream.Collectors;

public class WarManager {

    private final TownyConflict plugin;
    private final Map<UUID, War> activeWars = new HashMap<>();
    // townName -> liste des guerres actives
    private final Map<String, List<UUID>> townWarIndex = new HashMap<>();
    // cooldowns : "attacker:defender" -> timestamp fin cooldown
    private final Map<String, Long> redeclarationCooldowns = new HashMap<>();

    public WarManager(TownyConflict plugin) {
        this.plugin = plugin;
        loadWarsFromDatabase();
        startGraceChecker();
    }

    // ─────────────────────────────────────────
    //  DÉCLARATION
    // ─────────────────────────────────────────

    public enum DeclareResult {
        SUCCESS, SAME_NATION, TOO_MANY_WARS, ON_COOLDOWN,
        NOT_ENOUGH_MONEY, ALREADY_AT_WAR, CIVIL_WAR_DISABLED
    }

    public DeclareResult declareWar(Player attacker, String attackerTownName, String defenderTownName) {
        ConfigManager cfg = plugin.getConfigManager();

        // Vérif : guerre civile
        if (!cfg.allowCivilWar()) {
            Town aTown = TownyAPI.getInstance().getTown(attackerTownName);
            Town dTown = TownyAPI.getInstance().getTown(defenderTownName);
            if (aTown != null && dTown != null) {
                Nation aN = aTown.hasNation() ? aTown.getNationOrNull() : null;
                Nation dN = dTown.hasNation() ? dTown.getNationOrNull() : null;
                if (aN != null && dN != null && aN.getName().equals(dN.getName())) {
                    return DeclareResult.CIVIL_WAR_DISABLED;
                }
            }
        }

        // Vérif : déjà en guerre ensemble
        if (getWarBetween(attackerTownName, defenderTownName) != null) {
            return DeclareResult.ALREADY_AT_WAR;
        }

        // Vérif : max guerres simultanées
        int currentWars = getActiveWarsForTown(attackerTownName).size();
        if (currentWars >= cfg.getMaxSimultaneousWars()) {
            return DeclareResult.TOO_MANY_WARS;
        }

        // Vérif : cooldown redéclaration
        String cooldownKey = attackerTownName.toLowerCase() + ":" + defenderTownName.toLowerCase();
        if (redeclarationCooldowns.containsKey(cooldownKey)) {
            if (System.currentTimeMillis() < redeclarationCooldowns.get(cooldownKey)) {
                return DeclareResult.ON_COOLDOWN;
            }
        }

        // Vérif : argent (avec modificateur réputation)
        int rep = plugin.getReputationManager().getReputation(attackerTownName);
        double modifier = cfg.getDeclarationCostModifier(rep);
        double cost = cfg.getWarDeclarationCost() * modifier;
        Town aTown = TownyAPI.getInstance().getTown(attackerTownName);
        if (aTown == null || aTown.getAccount().getHoldingBalance() < cost) {
            return DeclareResult.NOT_ENOUGH_MONEY;
        }

        // Prélèvement
        aTown.getAccount().withdraw(cost, "Déclaration de guerre contre " + defenderTownName);

        // Création de la guerre (options par défaut, configurées ensuite via GUI)
        War war = new War(attackerTownName, defenderTownName);
        war.setAllowNationReinforcements(cfg.getWarOptionDefault("allow_nation_reinforcements"));
        war.setAllowMercenaries(cfg.getWarOptionDefault("allow_mercenaries"));
        war.setAllowAlliedNations(cfg.getWarOptionDefault("allow_allied_nations"));
        war.setVictoryConditionType("assault_wins");
        war.setVictoryConditionValue(cfg.getVictoryConditionDefault("assault_wins"));

        registerWar(war);

        // Réputation
        plugin.getReputationManager().addReputation(attackerTownName,
                cfg.getReputationChange("declare_war"));

        // Broadcast
        String msg = plugin.getConfigManager().getMessage("war_declared")
                .replace("{attacker}", attackerTownName)
                .replace("{defender}", defenderTownName);
        Bukkit.broadcastMessage(msg);

        // Grâce → activation automatique après délai
        long graceMs = cfg.getGracePeriodHours() * 3600_000L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> activateWar(war), graceMs / 50);

        return DeclareResult.SUCCESS;
    }

    private void activateWar(War war) {
        if (war.getStatus() == War.Status.GRACE_PERIOD) {
            war.setStatus(War.Status.ACTIVE);
            war.setStartedAt(System.currentTimeMillis());
            plugin.getDatabaseManager().saveWar(war);
            Bukkit.broadcastMessage(plugin.getConfigManager().getMessage("war_declared")
                    .replace("{attacker}", war.getAttackerTown())
                    .replace("{defender}", war.getDefenderTown()) + " §7— La période de grâce est terminée !");
        }
    }

    // ─────────────────────────────────────────
    //  FIN DE GUERRE
    // ─────────────────────────────────────────

    public void endWar(War war, String winner, War.EndReason reason) {
        war.setStatus(War.Status.ENDED);
        war.setWinnerTown(winner);
        war.setEndReason(reason);
        war.setEndedAt(System.currentTimeMillis());

        String loser = war.getOpponent(winner);

        // Réputation
        ReputationManager rep = plugin.getReputationManager();
        ConfigManager cfg = plugin.getConfigManager();
        rep.addReputation(winner, cfg.getReputationChange("win_war"));
        rep.addReputation(loser, cfg.getReputationChange("lose_war"));
        if (reason == War.EndReason.SURRENDER) {
            rep.addReputation(loser, cfg.getReputationChange("surrender"));
        }

        // Récompenses
        applyRewards(war, winner, loser);

        // Cooldown redéclaration
        long cooldownMs = cfg.getRedeclarationCooldownDays() * 86_400_000L;
        String key = war.getAttackerTown().toLowerCase() + ":" + war.getDefenderTown().toLowerCase();
        redeclarationCooldowns.put(key, System.currentTimeMillis() + cooldownMs);

        // Nettoyage
        unregisterWar(war);
        plugin.getDatabaseManager().saveWar(war);

        Bukkit.broadcastMessage(plugin.getConfigManager().getMessage("war_won")
                .replace("{winner}", winner)
                .replace("{loser}", loser));
    }

    private void applyRewards(War war, String winner, String loser) {
        Town loserTown = TownyAPI.getInstance().getTown(loser);
        if (loserTown == null) return;

        for (WarReward reward : war.getRewards()) {
            switch (reward.getType()) {
                case MONEY -> {
                    double balance = loserTown.getAccount().getHoldingBalance();
                    double amount = balance * (reward.getMoneyPercent() / 100.0);
                    loserTown.getAccount().withdraw(amount, "Récompense de guerre");
                    Town winnerTown = TownyAPI.getInstance().getTown(winner);
                    if (winnerTown != null)
                        winnerTown.getAccount().deposit(amount, "Récompense de guerre");
                }
                case NON_AGGRESSION -> {
                    long until = System.currentTimeMillis() + reward.getDurationDays() * 86_400_000L;
                    String key = loser.toLowerCase() + ":" + winner.toLowerCase();
                    redeclarationCooldowns.put(key, until);
                    String key2 = winner.toLowerCase() + ":" + loser.toLowerCase();
                    redeclarationCooldowns.put(key2, until);
                }
                // CLAIMS et VASSALIZATION nécessitent une interaction Towny avancée
                // implémentées selon la version de Towny utilisée
                default -> {}
            }
        }
    }

    // ─────────────────────────────────────────
    //  VÉRIFICATION VICTOIRE
    // ─────────────────────────────────────────

    public void checkVictory(War war) {
        if (!war.isActive()) return;
        if (war.checkVictory()) {
            endWar(war, war.getLeadingTown(), War.EndReason.VICTORY);
        }
    }

    // ─────────────────────────────────────────
    //  INDEX & LOOKUP
    // ─────────────────────────────────────────

    private void registerWar(War war) {
        activeWars.put(war.getId(), war);
        townWarIndex.computeIfAbsent(war.getAttackerTown().toLowerCase(), k -> new ArrayList<>()).add(war.getId());
        townWarIndex.computeIfAbsent(war.getDefenderTown().toLowerCase(), k -> new ArrayList<>()).add(war.getId());
        plugin.getDatabaseManager().saveWar(war);
    }

    private void unregisterWar(War war) {
        activeWars.remove(war.getId());
        List<UUID> a = townWarIndex.get(war.getAttackerTown().toLowerCase());
        if (a != null) a.remove(war.getId());
        List<UUID> d = townWarIndex.get(war.getDefenderTown().toLowerCase());
        if (d != null) d.remove(war.getId());
    }

    public War getWar(UUID id) { return activeWars.get(id); }

    public War getWarBetween(String town1, String town2) {
        List<UUID> ids = townWarIndex.get(town1.toLowerCase());
        if (ids == null) return null;
        for (UUID id : ids) {
            War w = activeWars.get(id);
            if (w != null && w.involves(town2)) return w;
        }
        return null;
    }

    public List<War> getActiveWarsForTown(String townName) {
        List<UUID> ids = townWarIndex.getOrDefault(townName.toLowerCase(), Collections.emptyList());
        return ids.stream().map(activeWars::get).filter(Objects::nonNull)
                .filter(w -> w.getStatus() != War.Status.ENDED).collect(Collectors.toList());
    }

    public Collection<War> getAllActiveWars() {
        return activeWars.values().stream()
                .filter(w -> w.getStatus() != War.Status.ENDED)
                .collect(Collectors.toList());
    }

    public long getCooldownRemaining(String attacker, String defender) {
        String key = attacker.toLowerCase() + ":" + defender.toLowerCase();
        Long until = redeclarationCooldowns.get(key);
        if (until == null) return 0;
        return Math.max(0, until - System.currentTimeMillis());
    }

    // ─────────────────────────────────────────
    //  TÂCHES PÉRIODIQUES
    // ─────────────────────────────────────────

    private void startGraceChecker() {
        // Vérif durée max des guerres toutes les heures
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int maxDays = plugin.getConfigManager().getMaxWarDurationDays();
            if (maxDays <= 0) return;
            for (War war : getAllActiveWars()) {
                if (war.getDaysElapsed() >= maxDays) {
                    endWar(war, war.getLeadingTown(), War.EndReason.TIMEOUT);
                }
            }
        }, 20L * 3600, 20L * 3600);
    }

    private void loadWarsFromDatabase() {
        List<War> wars = plugin.getDatabaseManager().loadActiveWars();
        for (War war : wars) {
            activeWars.put(war.getId(), war);
            townWarIndex.computeIfAbsent(war.getAttackerTown().toLowerCase(), k -> new ArrayList<>()).add(war.getId());
            townWarIndex.computeIfAbsent(war.getDefenderTown().toLowerCase(), k -> new ArrayList<>()).add(war.getId());
        }
    }
}
