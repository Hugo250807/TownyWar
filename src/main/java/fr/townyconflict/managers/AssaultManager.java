package fr.townyconflict.managers;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import fr.townyconflict.TownyConflict;
import fr.townyconflict.models.Assault;
import fr.townyconflict.models.War;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public class AssaultManager {

    private final TownyConflict plugin;
    private final Map<UUID, Assault> activeAssaults = new HashMap<>();
    // townName -> assaut en cours (pour lookup rapide)
    private final Map<String, UUID> townAssaultIndex = new HashMap<>();
    // warId -> dernier assaut (cooldown)
    private final Map<UUID, Long> lastAssaultTime = new HashMap<>();
    // assaultId -> tâche BukkitTask du timer
    private final Map<UUID, BukkitTask> assaultTasks = new HashMap<>();
    // assaultId -> tâche scoreboard
    private final Map<UUID, BukkitTask> scoreboardTasks = new HashMap<>();
    // assaultId -> tâche zone de capture (points toutes les 30 sec)
    private final Map<UUID, BukkitTask> captureTasks = new HashMap<>();

    public AssaultManager(TownyConflict plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────
    //  LANCEMENT
    // ─────────────────────────────────────────

    public enum StartResult {
        SUCCESS, NOT_IN_WAR, WAR_NOT_ACTIVE, ON_COOLDOWN,
        ALREADY_IN_ASSAULT, NOT_ENOUGH_DEFENDERS, NO_PERMISSION
    }

    public StartResult startAssault(Player initiator, String attackerTownName, String defenderTownName) {
        ConfigManager cfg = plugin.getConfigManager();
        War war = plugin.getWarManager().getWarBetween(attackerTownName, defenderTownName);

        if (war == null) return StartResult.NOT_IN_WAR;
        if (!war.isActive()) return StartResult.WAR_NOT_ACTIVE;

        // Vérif : déjà un assaut en cours entre ces towns
        if (townAssaultIndex.containsKey(defenderTownName.toLowerCase())) return StartResult.ALREADY_IN_ASSAULT;

        // Vérif : cooldown (sauf contre-attaque)
        long cooldownMs = cfg.getAssaultCooldownHours() * 3600_000L;
        Long last = lastAssaultTime.get(war.getId());
        if (last != null && !war.isCounterAttackAvailable()) {
            if (System.currentTimeMillis() - last < cooldownMs) return StartResult.ON_COOLDOWN;
        }

        // Vérif : défenseurs connectés
        Town defTown = TownyAPI.getInstance().getTown(defenderTownName);
        if (defTown == null) return StartResult.NOT_IN_WAR;
        long onlineDefenders = defTown.getResidents().stream()
                .filter(r -> Bukkit.getPlayer(r.getName()) != null).count();
        if (onlineDefenders < cfg.getMinDefendersOnline()) return StartResult.NOT_ENOUGH_DEFENDERS;

        // Création de l'assaut
        war.setCounterAttackAvailable(false);
        Assault assault = new Assault(war.getId(), attackerTownName, defenderTownName);
        assault.addAttacker(initiator.getUniqueId());

        // Point de ralliement attaquant (hors claims)
        Location spawn = defTown.getSpawnOrNull();
        if (spawn != null) {
            Location rally = spawn.clone().add(cfg.getRallyPointOffset(), 0, 0);
            assault.setRallyPoint(rally);
        }

        registerAssault(assault, war);
        startPhase(assault, Assault.Phase.MOBILIZATION, war);
        startScoreboard(assault);

        // Broadcast
        String msg = cfg.getMessage("assault_started")
                .replace("{attacker}", attackerTownName)
                .replace("{defender}", defenderTownName);
        Bukkit.broadcastMessage(msg);

        return StartResult.SUCCESS;
    }

    // ─────────────────────────────────────────
    //  PHASES
    // ─────────────────────────────────────────

    private void startPhase(Assault assault, Assault.Phase phase, War war) {
        assault.setCurrentPhase(phase);
        assault.resetPhaseScore();

        ConfigManager cfg = plugin.getConfigManager();
        int durationSec = cfg.getPhaseDuration(assault.getCurrentPhaseKey());
        assault.setPhaseTimeLeft(durationSec);

        // Annonce de la phase
        String phaseLabel = switch (phase) {
            case MOBILIZATION -> "§e⏳ Phase de mobilisation";
            case POINT1 -> "§c⚔ Point 1 — Frontière";
            case POINT2 -> "§c⚔ Point 2 — Centre";
            case FINAL_POINT -> "§4⚔ Point Final — Mairie";
            default -> "";
        };
        broadcastToParticipants(assault, phaseLabel + " §7(" + (durationSec / 60) + " min)");

        if (phase != Assault.Phase.MOBILIZATION) {
            spawnCaptureZone(assault, war);
            startCaptureTask(assault, war);
        }

        // Timer de phase
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int t = assault.getPhaseTimeLeft() - 1;
            assault.setPhaseTimeLeft(t);

            if (t <= 0) {
                // Temps écoulé : phase non réussie si capture
                if (phase != Assault.Phase.MOBILIZATION) {
                    // Si attaquants n'ont pas atteint le seuil → défenseurs gagnent la phase
                    int threshold = cfg.getCaptureThreshold(assault.getCurrentPhaseKey());
                    if (assault.getAttackerPhaseScore() < threshold) {
                        endAssault(assault, war, false);
                        return;
                    }
                }
                // Passer à la phase suivante
                cancelPhaseTask(assault.getId());
                cancelCaptureTask(assault.getId());
                advancePhase(assault, war);
            }
        }, 20L, 20L);

        assaultTasks.put(assault.getId(), task);
    }

    private void advancePhase(Assault assault, War war) {
        Assault.Phase next = switch (assault.getCurrentPhase()) {
            case MOBILIZATION -> Assault.Phase.POINT1;
            case POINT1 -> {
                assault.setCapturedPoints(1);
                yield Assault.Phase.POINT2;
            }
            case POINT2 -> {
                assault.setCapturedPoints(2);
                yield Assault.Phase.FINAL_POINT;
            }
            case FINAL_POINT -> {
                assault.setCapturedPoints(3);
                endAssault(assault, war, true);
                yield Assault.Phase.ENDED;
            }
            default -> Assault.Phase.ENDED;
        };
        if (next != Assault.Phase.ENDED) startPhase(assault, next, war);
    }

    // ─────────────────────────────────────────
    //  ZONE DE CAPTURE
    // ─────────────────────────────────────────

    private void spawnCaptureZone(Assault assault, War war) {
        Town defTown = TownyAPI.getInstance().getTown(assault.getDefenderTown());
        if (defTown == null) return;

        // Placement aléatoire dans les claims de la town
        Location spawn = defTown.getSpawnOrNull();
        if (spawn == null) return;

        Random rng = new Random();
        Location zoneCenter = spawn.clone().add(
                (rng.nextInt(21) - 10),
                0,
                (rng.nextInt(21) - 10)
        );
        assault.setCaptureZoneCenter(zoneCenter);

        broadcastToParticipants(assault, "§6★ Zone de capture apparue en §f" +
                zoneCenter.getBlockX() + ", " + zoneCenter.getBlockZ());
    }

    private void startCaptureTask(Assault assault, War war) {
        ConfigManager cfg = plugin.getConfigManager();
        int pts30 = cfg.getPointsPer30Sec(assault.getCurrentPhaseKey());
        int threshold = cfg.getCaptureThreshold(assault.getCurrentPhaseKey());

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!assault.isOngoing()) return;
            Location zone = assault.getCaptureZoneCenter();
            if (zone == null) return;

            int radius = cfg.getCaptureZoneRadius();
            int attackersInZone = 0, defendersInZone = 0;

            for (UUID uid : assault.getAttackerPlayers()) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null && p.getLocation().distance(zone) <= radius) attackersInZone++;
            }
            for (UUID uid : assault.getDefenderPlayers()) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null && p.getLocation().distance(zone) <= radius) defendersInZone++;
            }

            // Particules visuelles
            zone.getWorld().spawnParticle(Particle.FLAME, zone, 10, radius / 2.0, 0, radius / 2.0, 0);

            // Attribution des points à l'équipe qui contrôle
            if (attackersInZone > defendersInZone) {
                assault.setAttackerPhaseScore(assault.getAttackerPhaseScore() + pts30);
                broadcastToParticipants(assault, "§c[Zone] Attaquants contrôlent ! §f+" + pts30 + " pts §7(" +
                        assault.getAttackerPhaseScore() + "/" + threshold + ")");
                // Vérif seuil
                if (assault.getAttackerPhaseScore() >= threshold) {
                    cancelCaptureTask(assault.getId());
                    cancelPhaseTask(assault.getId());
                    String msg = cfg.getMessage("point_captured")
                            .replace("{point}", assault.getCurrentPhase().name())
                            .replace("{team}", assault.getAttackerTown());
                    broadcastToParticipants(assault, msg);
                    advancePhase(assault, war);
                }
            } else if (defendersInZone > attackersInZone) {
                assault.setDefenderPhaseScore(assault.getDefenderPhaseScore() + pts30);
                broadcastToParticipants(assault, "§a[Zone] Défenseurs contrôlent ! §f+" + pts30 + " pts");
            }

        }, 20L * 30, 20L * 30); // Toutes les 30 secondes

        captureTasks.put(assault.getId(), task);
    }

    // ─────────────────────────────────────────
    //  FIN D'ASSAUT
    // ─────────────────────────────────────────

    public void endAssault(Assault assault, War war, boolean attackerWon) {
        assault.setResult(attackerWon ? Assault.Result.ATTACKER_WIN : Assault.Result.DEFENDER_WIN);
        assault.setCurrentPhase(Assault.Phase.ENDED);

        cancelPhaseTask(assault.getId());
        cancelCaptureTask(assault.getId());
        cancelScoreboardTask(assault.getId());

        String winner = attackerWon ? assault.getAttackerTown() : assault.getDefenderTown();
        String loser = attackerWon ? assault.getDefenderTown() : assault.getAttackerTown();

        // Points de guerre
        int warPts = switch (assault.getCapturedPoints()) {
            case 1 -> plugin.getConfigManager().getWarPointsReward("point1_only");
            case 2 -> plugin.getConfigManager().getWarPointsReward("point2_captured");
            case 3 -> plugin.getConfigManager().getWarPointsReward("full_victory");
            default -> attackerWon ? 0 : plugin.getConfigManager().getWarPointsReward("defense_success");
        };

        war.addWarPoints(winner, warPts);
        war.addAssaultWin(winner);
        war.setLastAssaultEndTime(System.currentTimeMillis());
        lastAssaultTime.put(war.getId(), System.currentTimeMillis());

        // Broadcast résultat
        String msgKey = attackerWon ? "assault_won" : "assault_lost";
        String msg = plugin.getConfigManager().getMessage(msgKey)
                .replace("{winner}", winner).replace("{loser}", loser);
        Bukkit.broadcastMessage(msg);

        // Paiement mercenaires
        plugin.getMercenaryManager().resolveAssaultPayments(assault, attackerWon);

        // Fenêtre de contre-attaque
        ConfigManager cfg = plugin.getConfigManager();
        war.setCounterAttackAvailable(true);
        long windowMs = cfg.getCounterAttackWindowMinutes() * 60_000L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> war.setCounterAttackAvailable(false), windowMs / 50);

        // Nettoyage
        unregisterAssault(assault);
        plugin.getDatabaseManager().saveAssault(assault);

        // Vérif victoire de guerre
        plugin.getWarManager().checkVictory(war);
    }

    public void cancelAllAssaults() {
        for (Assault assault : new ArrayList<>(activeAssaults.values())) {
            cancelPhaseTask(assault.getId());
            cancelCaptureTask(assault.getId());
            cancelScoreboardTask(assault.getId());
        }
    }

    // ─────────────────────────────────────────
    //  REJOINDRE UN ASSAUT
    // ─────────────────────────────────────────

    public boolean joinAssault(Player player, String defenderTownName, boolean asAttacker) {
        UUID assaultId = townAssaultIndex.get(defenderTownName.toLowerCase());
        if (assaultId == null) return false;
        Assault assault = activeAssaults.get(assaultId);
        if (assault == null) return false;

        if (asAttacker) {
            assault.addAttacker(player.getUniqueId());
            if (assault.getRallyPoint() != null) player.teleport(assault.getRallyPoint());
        } else {
            assault.addDefender(player.getUniqueId());
            Town defTown = TownyAPI.getInstance().getTown(defenderTownName);
            if (defTown != null && defTown.getSpawnOrNull() != null)
                player.teleport(defTown.getSpawnOrNull());
        }
        return true;
    }

    // ─────────────────────────────────────────
    //  SCOREBOARD
    // ─────────────────────────────────────────

    private void startScoreboard(Assault assault) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!assault.isOngoing()) return;
            String line1 = "§c" + assault.getAttackerTown() + " §f" + assault.getAttackerPhaseScore();
            String line2 = "§a" + assault.getDefenderTown() + " §f" + assault.getDefenderPhaseScore();
            String line3 = "§7Temps: §f" + formatTime(assault.getPhaseTimeLeft());
            String line4 = "§7Phase: §f" + assault.getCurrentPhase().name();

            for (UUID uid : assault.getAttackerPlayers()) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null) {
                    p.sendActionBar(net.kyori.adventure.text.Component.text(
                            line1 + " §8| " + line2 + " §8| " + line3 + " §8| " + line4));
                }
            }
            for (UUID uid : assault.getDefenderPlayers()) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null) {
                    p.sendActionBar(net.kyori.adventure.text.Component.text(
                            line1 + " §8| " + line2 + " §8| " + line3 + " §8| " + line4));
                }
            }
        }, 20L, 20L);
        scoreboardTasks.put(assault.getId(), task);
    }

    // ─────────────────────────────────────────
    //  UTILS
    // ─────────────────────────────────────────

    private void registerAssault(Assault assault, War war) {
        activeAssaults.put(assault.getId(), assault);
        townAssaultIndex.put(assault.getDefenderTown().toLowerCase(), assault.getId());
        war.addAssaultId(assault.getId());
    }

    private void unregisterAssault(Assault assault) {
        activeAssaults.remove(assault.getId());
        townAssaultIndex.remove(assault.getDefenderTown().toLowerCase());
    }

    private void cancelPhaseTask(UUID assaultId) {
        BukkitTask t = assaultTasks.remove(assaultId);
        if (t != null) t.cancel();
    }
    private void cancelCaptureTask(UUID assaultId) {
        BukkitTask t = captureTasks.remove(assaultId);
        if (t != null) t.cancel();
    }
    private void cancelScoreboardTask(UUID assaultId) {
        BukkitTask t = scoreboardTasks.remove(assaultId);
        if (t != null) t.cancel();
    }

    private void broadcastToParticipants(Assault assault, String msg) {
        for (UUID uid : assault.getAttackerPlayers()) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) p.sendMessage(msg);
        }
        for (UUID uid : assault.getDefenderPlayers()) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) p.sendMessage(msg);
        }
    }

    private String formatTime(int seconds) {
        int m = seconds / 60, s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }

    public Assault getAssaultForTown(String townName) {
        UUID id = townAssaultIndex.get(townName.toLowerCase());
        return id != null ? activeAssaults.get(id) : null;
    }

    public Collection<Assault> getAllActiveAssaults() { return activeAssaults.values(); }

    public void onPlayerKill(Player killer, Player victim) {
        for (Assault assault : activeAssaults.values()) {
            if (!assault.isParticipant(killer.getUniqueId())) continue;
            if (!assault.isParticipant(victim.getUniqueId())) continue;
            if (assault.getCurrentPhase() == Assault.Phase.MOBILIZATION) continue;

            ConfigManager cfg = plugin.getConfigManager();
            int killPts = cfg.getPointsPerKill(assault.getCurrentPhaseKey());
            assault.addScore(killer.getUniqueId(), killPts);
            killer.sendMessage("§a+" + killPts + " pt(s) pour le kill !");
            break;
        }
    }
}
