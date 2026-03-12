package fr.townyconflict.managers;

import fr.townyconflict.TownyConflict;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final TownyConflict plugin;
    private FileConfiguration cfg;

    public ConfigManager(TownyConflict plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
    }

    // ── War ──
    public double getWarDeclarationCost() { return cfg.getDouble("war.declaration_cost", 500); }
    public int getGracePeriodHours() { return cfg.getInt("war.grace_period_hours", 24); }
    public int getMaxSimultaneousWars() { return cfg.getInt("war.max_simultaneous_wars", 2); }
    public int getRedeclarationCooldownDays() { return cfg.getInt("war.redeclaration_cooldown_days", 7); }
    public int getSurrenderMinDays() { return cfg.getInt("war.surrender_min_days", 3); }
    public boolean allowCivilWar() { return cfg.getBoolean("war.allow_civil_war", false); }
    public int getMaxWarDurationDays() { return cfg.getInt("war.max_war_duration_days", 30); }

    // ── Victory conditions availability ──
    public boolean isVictoryConditionEnabled(String type) {
        return cfg.getBoolean("war.victory_conditions." + type + ".enabled", true);
    }
    public int getVictoryConditionMin(String type) {
        return cfg.getInt("war.victory_conditions." + type + ".min", 1);
    }
    public int getVictoryConditionMax(String type) {
        return cfg.getInt("war.victory_conditions." + type + ".max", 20);
    }
    public int getVictoryConditionDefault(String type) {
        return cfg.getInt("war.victory_conditions." + type + ".default", 5);
    }

    // ── War options defaults ──
    public boolean isWarOptionEnabled(String option) {
        return cfg.getBoolean("war.options." + option + ".enabled", true);
    }
    public boolean getWarOptionDefault(String option) {
        return cfg.getBoolean("war.options." + option + ".default", false);
    }

    // ── Rewards ──
    public boolean isRewardEnabled(String reward) {
        return cfg.getBoolean("war.rewards." + reward + ".enabled", true);
    }
    public double getMoneyRewardMaxPercent() { return cfg.getDouble("war.rewards.money.max_percentage", 15); }
    public double getMoneyRewardDefaultPercent() { return cfg.getDouble("war.rewards.money.default_percentage", 10); }
    public int getClaimsRewardMaxPlots() { return cfg.getInt("war.rewards.claims.max_plots", 10); }
    public int getVassalizationDays() { return cfg.getInt("war.rewards.vassalization.duration_days", 14); }
    public int getNonAggressionMinDays() { return cfg.getInt("war.rewards.non_aggression.min_days", 3); }
    public int getNonAggressionMaxDays() { return cfg.getInt("war.rewards.non_aggression.max_days", 30); }
    public int getNonAggressionDefaultDays() { return cfg.getInt("war.rewards.non_aggression.default_days", 7); }

    // ── Assault ──
    public int getAssaultCooldownHours() { return cfg.getInt("assault.cooldown_hours", 2); }
    public int getCounterAttackWindowMinutes() { return cfg.getInt("assault.counter_attack_window_minutes", 5); }
    public int getMinDefendersOnline() { return cfg.getInt("assault.min_defenders_online", 2); }
    public int getPhaseDuration(String phase) {
        return cfg.getInt("assault.phases." + phase + ".duration_seconds", 600);
    }
    public int getCaptureThreshold(String phase) {
        return cfg.getInt("assault.phases." + phase + ".capture_threshold", 15);
    }
    public int getPointsPer30Sec(String phase) {
        return cfg.getInt("assault.phases." + phase + ".points_per_30sec", 1);
    }
    public int getPointsPerKill(String phase) {
        return cfg.getInt("assault.phases." + phase + ".points_per_kill", 1);
    }
    public int getWarPointsReward(String result) {
        return cfg.getInt("assault.war_points_reward." + result, 1);
    }
    public int getCaptureZoneRadius() { return cfg.getInt("assault.capture_zone.radius", 10); }
    public int getNoBuildRadius() { return cfg.getInt("assault.capture_zone.no_build_radius", 28); }
    public int getRallyPointOffset() { return cfg.getInt("assault.rally_point_offset", 30); }

    // ── Reinforcements ──
    public int getReinforcementQuota(int nationSize) {
        if (nationSize <= 1) return cfg.getInt("reinforcements.quotas.solo", 0);
        int smallMax = cfg.getInt("reinforcements.thresholds.small_max", 4);
        int mediumMax = cfg.getInt("reinforcements.thresholds.medium_max", 9);
        if (nationSize <= smallMax) return cfg.getInt("reinforcements.quotas.small", 2);
        if (nationSize <= mediumMax) return cfg.getInt("reinforcements.quotas.medium", 4);
        return cfg.getInt("reinforcements.quotas.large", 6);
    }

    // ── Mercenaries ──
    public int getMercMaxPerTeam() { return cfg.getInt("mercenaries.max_per_team", 3); }
    public int getMercAcceptTimeoutSeconds() { return cfg.getInt("mercenaries.accept_timeout_seconds", 600); }
    public int getMercUpfrontPaymentPercent() { return cfg.getInt("mercenaries.upfront_payment_percent", 50); }
    public int getMercDesertionCooldownDays() { return cfg.getInt("mercenaries.desertion_cooldown_days", 3); }
    public boolean isMercOnlyNationless() { return cfg.getBoolean("mercenaries.only_nationless", false); }
    public int getMercReputationCost() { return cfg.getInt("mercenaries.reputation_cost", 2); }

    // ── Reputation ──
    public boolean isReputationEnabled() { return cfg.getBoolean("reputation.enabled", true); }
    public int getReputationChange(String action) {
        return cfg.getInt("reputation.changes." + action, 0);
    }
    public int getMercRefuseThreshold() { return cfg.getInt("reputation.mercenary_refuse_threshold", -30); }
    public double getDeclarationCostModifier(int reputation) {
        if (reputation < -50) return cfg.getDouble("reputation.declaration_cost_modifier.below_minus_50", 2.0);
        if (reputation < -20) return cfg.getDouble("reputation.declaration_cost_modifier.below_minus_20", 1.5);
        if (reputation > 50) return cfg.getDouble("reputation.declaration_cost_modifier.above_50", 0.75);
        return 1.0;
    }

    // ── Treaties ──
    public int getTreatyAvailableAfterDays() { return cfg.getInt("treaties.available_after_days", 3); }
    public int getTreatyResponseTimeoutHours() { return cfg.getInt("treaties.response_timeout_hours", 24); }
    public boolean isTreatyTypeEnabled(String type) {
        return cfg.getBoolean("treaties.types." + type + ".enabled", true);
    }

    // ── Messages ──
    public String getMessage(String key) {
        String prefix = cfg.getString("messages.prefix", "&8[&cTC&8] ");
        String msg = cfg.getString("messages." + key, "&cMessage introuvable: " + key);
        return colorize(prefix + msg);
    }
    public String getRawMessage(String key) {
        return colorize(cfg.getString("messages." + key, "&cMessage introuvable: " + key));
    }

    // ── GUI ──
    public String getGuiTitle(String key) {
        return colorize(cfg.getString("gui.titles." + key, "&8Menu"));
    }
    public Material getGuiMaterial(String key) {
        String mat = cfg.getString("gui.materials." + key, "PAPER");
        try { return Material.valueOf(mat.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.PAPER; }
    }

    // ── Debug ──
    public boolean isDebug() { return cfg.getBoolean("debug", false); }

    // ── Utils ──
    private String colorize(String s) {
        return s.replace("&", "§");
    }
}
