package fr.townyconflict.models;

import java.util.*;

public class War {

    public enum Status { GRACE_PERIOD, ACTIVE, PEACE_NEGOTIATION, ENDED }
    public enum EndReason { VICTORY, SURRENDER, TREATY, TIMEOUT }

    // ── Identity ──
    private final UUID id;
    private final String attackerTown;
    private final String defenderTown;
    private final long declaredAt;

    // ── Status ──
    private Status status;
    private long startedAt;   // après la grâce
    private long endedAt;
    private EndReason endReason;
    private String winnerTown;

    // ── Options choisies par les joueurs à la déclaration ──
    private boolean allowNationReinforcements;
    private boolean allowMercenaries;
    private boolean allowAlliedNations;

    // ── Conditions de victoire ──
    private String victoryConditionType;  // "assault_wins", "war_points", "time_limit"
    private int victoryConditionValue;

    // ── Récompenses choisies ──
    private final List<WarReward> rewards = new ArrayList<>();

    // ── Progression ──
    private int attackerWarPoints;
    private int defenderWarPoints;
    private int attackerAssaultWins;
    private int defenderAssaultWins;
    private final List<UUID> assaultIds = new ArrayList<>();

    // ── Cooldown assaut ──
    private long lastAssaultEndTime;
    private boolean counterAttackAvailable;

    public War(String attackerTown, String defenderTown) {
        this.id = UUID.randomUUID();
        this.attackerTown = attackerTown;
        this.defenderTown = defenderTown;
        this.declaredAt = System.currentTimeMillis();
        this.status = Status.GRACE_PERIOD;
    }

    // ── Helpers ──
    public boolean isActive() { return status == Status.ACTIVE; }
    public boolean isInGrace() { return status == Status.GRACE_PERIOD; }
    public long getDaysElapsed() {
        long ref = startedAt > 0 ? startedAt : declaredAt;
        return (System.currentTimeMillis() - ref) / (1000L * 60 * 60 * 24);
    }
    public boolean involves(String townName) {
        return attackerTown.equalsIgnoreCase(townName) || defenderTown.equalsIgnoreCase(townName);
    }
    public String getOpponent(String townName) {
        if (attackerTown.equalsIgnoreCase(townName)) return defenderTown;
        return attackerTown;
    }
    public boolean isAttacker(String townName) { return attackerTown.equalsIgnoreCase(townName); }

    public void addWarPoints(String townName, int points) {
        if (isAttacker(townName)) attackerWarPoints += points;
        else defenderWarPoints += points;
    }
    public void addAssaultWin(String townName) {
        if (isAttacker(townName)) attackerAssaultWins++;
        else defenderAssaultWins++;
    }
    public int getWarPoints(String townName) {
        return isAttacker(townName) ? attackerWarPoints : defenderWarPoints;
    }

    public boolean checkVictory() {
        switch (victoryConditionType) {
            case "assault_wins":
                return attackerAssaultWins >= victoryConditionValue || defenderAssaultWins >= victoryConditionValue;
            case "war_points":
                return attackerWarPoints >= victoryConditionValue || defenderWarPoints >= victoryConditionValue;
            case "time_limit":
                return getDaysElapsed() >= victoryConditionValue;
            default: return false;
        }
    }
    public String getLeadingTown() {
        if ("time_limit".equals(victoryConditionType)) {
            return attackerWarPoints >= defenderWarPoints ? attackerTown : defenderTown;
        }
        int a = "assault_wins".equals(victoryConditionType) ? attackerAssaultWins : attackerWarPoints;
        int d = "assault_wins".equals(victoryConditionType) ? defenderAssaultWins : defenderWarPoints;
        return a >= d ? attackerTown : defenderTown;
    }

    // ── Getters / Setters ──
    public UUID getId() { return id; }
    public String getAttackerTown() { return attackerTown; }
    public String getDefenderTown() { return defenderTown; }
    public long getDeclaredAt() { return declaredAt; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public long getStartedAt() { return startedAt; }
    public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
    public long getEndedAt() { return endedAt; }
    public void setEndedAt(long endedAt) { this.endedAt = endedAt; }
    public EndReason getEndReason() { return endReason; }
    public void setEndReason(EndReason endReason) { this.endReason = endReason; }
    public String getWinnerTown() { return winnerTown; }
    public void setWinnerTown(String winnerTown) { this.winnerTown = winnerTown; }
    public boolean isAllowNationReinforcements() { return allowNationReinforcements; }
    public void setAllowNationReinforcements(boolean v) { this.allowNationReinforcements = v; }
    public boolean isAllowMercenaries() { return allowMercenaries; }
    public void setAllowMercenaries(boolean v) { this.allowMercenaries = v; }
    public boolean isAllowAlliedNations() { return allowAlliedNations; }
    public void setAllowAlliedNations(boolean v) { this.allowAlliedNations = v; }
    public String getVictoryConditionType() { return victoryConditionType; }
    public void setVictoryConditionType(String v) { this.victoryConditionType = v; }
    public int getVictoryConditionValue() { return victoryConditionValue; }
    public void setVictoryConditionValue(int v) { this.victoryConditionValue = v; }
    public List<WarReward> getRewards() { return rewards; }
    public void addReward(WarReward reward) { rewards.add(reward); }
    public int getAttackerWarPoints() { return attackerWarPoints; }
    public int getDefenderWarPoints() { return defenderWarPoints; }
    public int getAttackerAssaultWins() { return attackerAssaultWins; }
    public int getDefenderAssaultWins() { return defenderAssaultWins; }
    public List<UUID> getAssaultIds() { return assaultIds; }
    public void addAssaultId(UUID id) { assaultIds.add(id); }
    public long getLastAssaultEndTime() { return lastAssaultEndTime; }
    public void setLastAssaultEndTime(long t) { this.lastAssaultEndTime = t; }
    public boolean isCounterAttackAvailable() { return counterAttackAvailable; }
    public void setCounterAttackAvailable(boolean v) { this.counterAttackAvailable = v; }
}
