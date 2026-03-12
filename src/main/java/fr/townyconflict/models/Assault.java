package fr.townyconflict.models;

import org.bukkit.Location;
import java.util.*;

public class Assault {

    public enum Phase {
        MOBILIZATION, POINT1, POINT2, FINAL_POINT, ENDED
    }
    public enum Result { ATTACKER_WIN, DEFENDER_WIN, ONGOING }

    private final UUID id;
    private final UUID warId;
    private final String attackerTown;
    private final String defenderTown;
    private final long startedAt;

    private Phase currentPhase = Phase.MOBILIZATION;
    private Result result = Result.ONGOING;

    // Score de la phase en cours
    private int attackerPhaseScore;
    private int defenderPhaseScore;

    // Points capturés (0, 1, 2 ou 3)
    private int capturedPoints;

    // Localisation de la zone de capture active
    private Location captureZoneCenter;
    private Location rallyPoint;

    // Participants
    private final Set<UUID> attackerPlayers = new HashSet<>();
    private final Set<UUID> defenderPlayers = new HashSet<>();
    private final Set<UUID> mercenaries = new HashSet<>();
    private final Set<UUID> reinforcements = new HashSet<>();

    // Timer courant (en secondes restantes)
    private int phaseTimeLeft;

    public Assault(UUID warId, String attackerTown, String defenderTown) {
        this.id = UUID.randomUUID();
        this.warId = warId;
        this.attackerTown = attackerTown;
        this.defenderTown = defenderTown;
        this.startedAt = System.currentTimeMillis();
    }

    public void addAttacker(UUID player) { attackerPlayers.add(player); }
    public void addDefender(UUID player) { defenderPlayers.add(player); }
    public void removePlayer(UUID player) {
        attackerPlayers.remove(player);
        defenderPlayers.remove(player);
        mercenaries.remove(player);
        reinforcements.remove(player);
    }

    public boolean isAttacker(UUID player) { return attackerPlayers.contains(player); }
    public boolean isDefender(UUID player) { return defenderPlayers.contains(player); }
    public boolean isParticipant(UUID player) {
        return attackerPlayers.contains(player) || defenderPlayers.contains(player);
    }

    public void addScore(UUID player, int points) {
        if (isAttacker(player)) attackerPhaseScore += points;
        else if (isDefender(player)) defenderPhaseScore += points;
    }

    public void resetPhaseScore() {
        attackerPhaseScore = 0;
        defenderPhaseScore = 0;
    }

    public boolean isOngoing() { return result == Result.ONGOING; }

    public String getCurrentPhaseKey() {
        return switch (currentPhase) {
            case MOBILIZATION -> "mobilization";
            case POINT1 -> "point1";
            case POINT2 -> "point2";
            case FINAL_POINT -> "final_point";
            default -> "final_point";
        };
    }

    // ── Getters/Setters ──
    public UUID getId() { return id; }
    public UUID getWarId() { return warId; }
    public String getAttackerTown() { return attackerTown; }
    public String getDefenderTown() { return defenderTown; }
    public long getStartedAt() { return startedAt; }
    public Phase getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(Phase p) { this.currentPhase = p; }
    public Result getResult() { return result; }
    public void setResult(Result r) { this.result = r; }
    public int getAttackerPhaseScore() { return attackerPhaseScore; }
    public int getDefenderPhaseScore() { return defenderPhaseScore; }
    public void setAttackerPhaseScore(int v) { this.attackerPhaseScore = v; }
    public void setDefenderPhaseScore(int v) { this.defenderPhaseScore = v; }
    public int getCapturedPoints() { return capturedPoints; }
    public void setCapturedPoints(int v) { this.capturedPoints = v; }
    public Location getCaptureZoneCenter() { return captureZoneCenter; }
    public void setCaptureZoneCenter(Location l) { this.captureZoneCenter = l; }
    public Location getRallyPoint() { return rallyPoint; }
    public void setRallyPoint(Location l) { this.rallyPoint = l; }
    public Set<UUID> getAttackerPlayers() { return attackerPlayers; }
    public Set<UUID> getDefenderPlayers() { return defenderPlayers; }
    public Set<UUID> getMercenaries() { return mercenaries; }
    public void addMercenary(UUID player) { mercenaries.add(player); }
    public Set<UUID> getReinforcements() { return reinforcements; }
    public void addReinforcement(UUID player) { reinforcements.add(player); }
    public int getPhaseTimeLeft() { return phaseTimeLeft; }
    public void setPhaseTimeLeft(int t) { this.phaseTimeLeft = t; }
}
