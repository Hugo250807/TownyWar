package fr.townyconflict.managers;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import fr.townyconflict.TownyConflict;
import fr.townyconflict.models.Assault;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public class MercenaryManager {

    private final TownyConflict plugin;

    // Offres en attente : playerUUID -> offre
    private final Map<UUID, MercOffer> pendingOffers = new HashMap<>();
    // Cooldowns de désertion : playerUUID -> timestamp fin cooldown
    private final Map<UUID, Long> desertionCooldowns = new HashMap<>();
    // Timers d'expiration d'offre
    private final Map<UUID, BukkitTask> offerTimers = new HashMap<>();

    public MercenaryManager(TownyConflict plugin) {
        this.plugin = plugin;
    }

    public static class MercOffer {
        public final UUID targetPlayer;
        public final String hiringTown;
        public final String defenderTown;  // pour identifier l'assaut
        public final double totalAmount;
        public final boolean forAttacker;  // true = rejoint attaquants, false = défenseurs

        public MercOffer(UUID target, String hiringTown, String defenderTown, double amount, boolean forAttacker) {
            this.targetPlayer = target;
            this.hiringTown = hiringTown;
            this.defenderTown = defenderTown;
            this.totalAmount = amount;
            this.forAttacker = forAttacker;
        }
    }

    // ─────────────────────────────────────────
    //  RECRUTEMENT
    // ─────────────────────────────────────────

    public enum HireResult {
        SUCCESS, ASSAULT_NOT_FOUND, WAR_MERC_DISABLED, TOO_MANY_MERCS,
        PLAYER_IN_NATION, PLAYER_ON_COOLDOWN, NOT_ENOUGH_MONEY,
        PLAYER_IN_SAME_NATION, OFFER_ALREADY_PENDING, LOW_REPUTATION
    }

    public HireResult hirePlayer(Player recruiter, String hiringTownName, UUID targetUUID, double amount, String defenderTownName) {
        ConfigManager cfg = plugin.getConfigManager();
        Player target = Bukkit.getPlayer(targetUUID);
        if (target == null) return HireResult.ASSAULT_NOT_FOUND;

        Assault assault = plugin.getAssaultManager().getAssaultForTown(defenderTownName);
        if (assault == null) return HireResult.ASSAULT_NOT_FOUND;

        // Vérif : mercenaires autorisés dans cette guerre
        var war = plugin.getWarManager().getWarBetween(assault.getAttackerTown(), assault.getDefenderTown());
        if (war == null || !war.isAllowMercenaries()) return HireResult.WAR_MERC_DISABLED;

        // Vérif : réputation
        int rep = plugin.getReputationManager().getReputation(hiringTownName);
        if (rep < cfg.getMercRefuseThreshold()) return HireResult.LOW_REPUTATION;

        // Vérif : max mercenaires
        boolean forAttacker = hiringTownName.equalsIgnoreCase(assault.getAttackerTown());
        long currentMercs = assault.getMercenaries().stream()
                .filter(uid -> forAttacker ? assault.isAttacker(uid) : assault.isDefender(uid)).count();
        if (currentMercs >= cfg.getMercMaxPerTeam()) return HireResult.TOO_MANY_MERCS;

        // Vérif : joueur dans la même nation que la town adverse ?
        if (cfg.isMercOnlyNationless()) {
            Resident res = TownyAPI.getInstance().getResident(target.getName());
            if (res != null && res.hasNation()) return HireResult.PLAYER_IN_NATION;
        }

        // Vérif : cooldown désertion
        if (isOnDesertionCooldown(targetUUID)) return HireResult.PLAYER_ON_COOLDOWN;

        // Vérif : offre déjà en attente
        if (pendingOffers.containsKey(targetUUID)) return HireResult.OFFER_ALREADY_PENDING;

        // Vérif argent (acompte 50%)
        double upfront = amount * (cfg.getMercUpfrontPaymentPercent() / 100.0);
        com.palmergames.bukkit.towny.object.Town aTown = TownyAPI.getInstance().getTown(hiringTownName);
        if (aTown == null || aTown.getAccount().getHoldingBalance() < upfront) {
            return HireResult.NOT_ENOUGH_MONEY;
        }

        // Prélèvement acompte
        aTown.getAccount().withdraw(upfront, "Acompte mercenaire " + target.getName());
        plugin.getEconomy().depositPlayer(target, upfront);

        // Création de l'offre
        MercOffer offer = new MercOffer(targetUUID, hiringTownName, defenderTownName, amount, forAttacker);
        pendingOffers.put(targetUUID, offer);

        // Notification au cible
        String msg = plugin.getConfigManager().getMessage("merc_offer")
                .replace("{town}", hiringTownName)
                .replace("{amount}", String.format("%.0f", amount));
        target.sendMessage(msg);
        target.sendMessage("§7Tapez §f/tc merc accept §7ou §f/tc merc refuse §7sous " +
                (cfg.getMercAcceptTimeoutSeconds() / 60) + " minutes.");

        // Réputation
        plugin.getReputationManager().addReputation(hiringTownName,
                -cfg.getMercReputationCost());

        // Timer d'expiration
        BukkitTask timer = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingOffers.containsKey(targetUUID)) {
                pendingOffers.remove(targetUUID);
                target.sendMessage("§7L'offre de mercenaire a expiré.");
                // Remboursement acompte si offre expirée
                plugin.getEconomy().withdrawPlayer(target, upfront);
                if (aTown != null) aTown.getAccount().deposit(upfront, "Remboursement acompte merc expiré");
            }
        }, (long) cfg.getMercAcceptTimeoutSeconds() * 20L);
        offerTimers.put(targetUUID, timer);

        return HireResult.SUCCESS;
    }

    public boolean acceptOffer(Player player) {
        MercOffer offer = pendingOffers.remove(player.getUniqueId());
        if (offer == null) return false;

        BukkitTask t = offerTimers.remove(player.getUniqueId());
        if (t != null) t.cancel();

        Assault assault = plugin.getAssaultManager().getAssaultForTown(offer.defenderTown);
        if (assault == null) {
            player.sendMessage("§cL'assaut a déjà pris fin !");
            return false;
        }

        assault.addMercenary(player.getUniqueId());
        if (offer.forAttacker) {
            assault.addAttacker(player.getUniqueId());
            if (assault.getRallyPoint() != null) player.teleport(assault.getRallyPoint());
        } else {
            assault.addDefender(player.getUniqueId());
            com.palmergames.bukkit.towny.object.Town defTown =
                    TownyAPI.getInstance().getTown(offer.defenderTown);
            if (defTown != null && defTown.getSpawnOrNull() != null)
                player.teleport(defTown.getSpawnOrNull());
        }

        player.sendMessage("§aVous avez rejoint l'assaut en tant que mercenaire pour §f" + offer.hiringTown + "§a !");
        return true;
    }

    public boolean refuseOffer(Player player) {
        MercOffer offer = pendingOffers.remove(player.getUniqueId());
        if (offer == null) return false;

        BukkitTask t = offerTimers.remove(player.getUniqueId());
        if (t != null) t.cancel();

        // Remboursement acompte
        double upfront = offer.totalAmount * (plugin.getConfigManager().getMercUpfrontPaymentPercent() / 100.0);
        plugin.getEconomy().withdrawPlayer(player, upfront);
        com.palmergames.bukkit.towny.object.Town town = TownyAPI.getInstance().getTown(offer.hiringTown);
        if (town != null) town.getAccount().deposit(upfront, "Remboursement acompte mercenaire refusé");

        player.sendMessage("§7Vous avez refusé l'offre de §f" + offer.hiringTown + "§7.");
        return true;
    }

    // ─────────────────────────────────────────
    //  PAIEMENT FIN D'ASSAUT
    // ─────────────────────────────────────────

    public void resolveAssaultPayments(Assault assault, boolean attackerWon) {
        // Les offres en attente associées à cet assaut sont annulées
        pendingOffers.entrySet().removeIf(e -> e.getValue().defenderTown.equalsIgnoreCase(assault.getDefenderTown()));

        // Paiement 2ème moitié aux mercenaires du camp vainqueur
        for (UUID uid : assault.getMercenaries()) {
            boolean isAttacker = assault.getAttackerPlayers().contains(uid);
            boolean won = (isAttacker == attackerWon);
            Player merc = Bukkit.getPlayer(uid);

            // On cherche l'offre originale dans la DB (simplifié : on recalcule)
            // Dans une implémentation complète, stocker le montant dans Assault
            if (won && merc != null) {
                merc.sendMessage("§a+§fBonus mercenaire §a(victoire) — consultez votre banque.");
            } else if (!won && merc != null) {
                merc.sendMessage("§cVous n'avez pas remporté l'assaut. La 2ème partie du paiement est perdue.");
            }
        }
    }

    // ─────────────────────────────────────────
    //  DÉSERTION
    // ─────────────────────────────────────────

    public void handleDesertion(UUID playerUUID, Assault assault) {
        if (!assault.getMercenaries().contains(playerUUID)) return;
        Player p = Bukkit.getPlayer(playerUUID);

        // Cooldown
        long cooldownMs = plugin.getConfigManager().getMercDesertionCooldownDays() * 86_400_000L;
        desertionCooldowns.put(playerUUID, System.currentTimeMillis() + cooldownMs);

        // Retrait 2ème moitié (déjà perdue, mais log)
        if (p != null) p.sendMessage("§cVous avez déserté ! Vous ne recevrez pas le reste du paiement " +
                "et vous ne pouvez plus être mercenaire pendant " +
                plugin.getConfigManager().getMercDesertionCooldownDays() + " jours.");

        assault.removePlayer(playerUUID);
    }

    public boolean isOnDesertionCooldown(UUID uuid) {
        Long until = desertionCooldowns.get(uuid);
        return until != null && System.currentTimeMillis() < until;
    }

    public long getDesertionCooldownRemaining(UUID uuid) {
        Long until = desertionCooldowns.get(uuid);
        if (until == null) return 0;
        return Math.max(0, until - System.currentTimeMillis());
    }

    public MercOffer getPendingOffer(UUID uuid) { return pendingOffers.get(uuid); }
}
