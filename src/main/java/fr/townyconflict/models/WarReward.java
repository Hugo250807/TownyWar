package fr.townyconflict.models;

public class WarReward {

    public enum Type { MONEY, CLAIMS, VASSALIZATION, NON_AGGRESSION }

    private final Type type;
    private double moneyPercent;
    private int claimsCount;
    private int durationDays;

    public WarReward(Type type) { this.type = type; }

    public static WarReward money(double percent) {
        WarReward r = new WarReward(Type.MONEY);
        r.moneyPercent = percent;
        return r;
    }
    public static WarReward claims(int count) {
        WarReward r = new WarReward(Type.CLAIMS);
        r.claimsCount = count;
        return r;
    }
    public static WarReward vassalization(int days) {
        WarReward r = new WarReward(Type.VASSALIZATION);
        r.durationDays = days;
        return r;
    }
    public static WarReward nonAggression(int days) {
        WarReward r = new WarReward(Type.NON_AGGRESSION);
        r.durationDays = days;
        return r;
    }

    public String describe() {
        return switch (type) {
            case MONEY -> String.format("%.0f%% de la banque adverse", moneyPercent);
            case CLAIMS -> claimsCount + " plots revendiqués";
            case VASSALIZATION -> "Vassalisation " + durationDays + " jours";
            case NON_AGGRESSION -> "Non-agression " + durationDays + " jours";
        };
    }

    public Type getType() { return type; }
    public double getMoneyPercent() { return moneyPercent; }
    public int getClaimsCount() { return claimsCount; }
    public int getDurationDays() { return durationDays; }
}
