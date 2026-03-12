package fr.townyconflict.managers;

import fr.townyconflict.TownyConflict;
import fr.townyconflict.models.War;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TreatyManager {

    public enum TreatyType { PEACE, NON_AGGRESSION, CEASEFIRE }

    public static class TreatyProposal {
        public final UUID warId;
        public final String proposerTown;
        public final String targetTown;
        public final TreatyType type;
        public final int days;
        public final long proposedAt;

        public TreatyProposal(UUID warId, String proposer, String target, TreatyType type, int days) {
            this.warId = warId;
            this.proposerTown = proposer;
            this.targetTown = target;
            this.type = type;
            this.days = days;
            this.proposedAt = System.currentTimeMillis();
        }
    }

    private final TownyConflict plugin;
    // warId -> proposition en attente
    private final Map<UUID, TreatyProposal> pendingProposals = new HashMap<>();
    private final Map<UUID, BukkitTask> expiryTimers = new HashMap<>();

    public TreatyManager(TownyConflict plugin) {
        this.plugin = plugin;
    }

    public enum ProposeResult {
        SUCCESS, WAR_NOT_FOUND, TOO_EARLY, TYPE_DISABLED, PROPOSAL_PENDING
    }

    public ProposeResult propose(String proposerTown, String targetTown, TreatyType type, int days) {
        War war = plugin.getWarManager().getWarBetween(proposerTown, targetTown);
        if (war == null || !war.isActive()) return ProposeResult.WAR_NOT_FOUND;

        // Vérif : trop tôt
        if (war.getDaysElapsed() < plugin.getConfigManager().getTreatyAvailableAfterDays()) {
            return ProposeResult.TOO_EARLY;
        }

        // Vérif : type activé
        String typeKey = type.name().toLowerCase();
        if (!plugin.getConfigManager().isTreatyTypeEnabled(typeKey)) return ProposeResult.TYPE_DISABLED;

        // Vérif : pas de proposition déjà en attente
        if (pendingProposals.containsKey(war.getId())) return ProposeResult.PROPOSAL_PENDING;

        TreatyProposal proposal = new TreatyProposal(war.getId(), proposerTown, targetTown, type, days);
        pendingProposals.put(war.getId(), proposal);

        // Notification
        String msg = plugin.getConfigManager().getMessage("treaty_proposed")
                .replace("{sender}", proposerTown)
                .replace("{type}", type.name());
        notifyTown(targetTown, msg);
        notifyTown(targetTown, "§7Tapez §f/tc treaty accept §7ou §f/tc treaty refuse §7(expire sous " +
                plugin.getConfigManager().getTreatyResponseTimeoutHours() + "h)");

        // Timer d'expiration
        long timeoutMs = plugin.getConfigManager().getTreatyResponseTimeoutHours() * 3600_000L;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingProposals.remove(war.getId()) != null) {
                notifyTown(proposerTown, "§7Le traité proposé à §f" + targetTown + " §7a expiré.");
            }
        }, timeoutMs / 50);
        expiryTimers.put(war.getId(), task);

        return ProposeResult.SUCCESS;
    }

    public boolean accept(String acceptorTown) {
        for (TreatyProposal proposal : pendingProposals.values()) {
            if (proposal.targetTown.equalsIgnoreCase(acceptorTown)) {
                pendingProposals.remove(proposal.warId);
                BukkitTask t = expiryTimers.remove(proposal.warId);
                if (t != null) t.cancel();

                applyTreaty(proposal);
                return true;
            }
        }
        return false;
    }

    public boolean refuse(String refusorTown) {
        for (TreatyProposal proposal : pendingProposals.values()) {
            if (proposal.targetTown.equalsIgnoreCase(refusorTown)) {
                pendingProposals.remove(proposal.warId);
                BukkitTask t = expiryTimers.remove(proposal.warId);
                if (t != null) t.cancel();
                notifyTown(proposal.proposerTown, "§c" + refusorTown + " a refusé votre traité.");
                return true;
            }
        }
        return false;
    }

    private void applyTreaty(TreatyProposal proposal) {
        War war = plugin.getWarManager().getWar(proposal.warId);
        if (war == null) return;

        switch (proposal.type) {
            case PEACE -> {
                plugin.getWarManager().endWar(war, proposal.proposerTown, War.EndReason.TREATY);
                plugin.getReputationManager().addReputation(proposal.proposerTown,
                        plugin.getConfigManager().getReputationChange("honor_treaty"));
                plugin.getReputationManager().addReputation(proposal.targetTown,
                        plugin.getConfigManager().getReputationChange("honor_treaty"));
                Bukkit.broadcastMessage("§b🕊 Traité de paix signé entre §f" +
                        proposal.proposerTown + " §bet §f" + proposal.targetTown + "§b !");
            }
            case NON_AGGRESSION -> {
                // Stocker la non-agression (cooldown redéclaration)
                notifyTown(proposal.proposerTown, "§bAccord de non-agression actif pour " + proposal.days + " jours.");
                notifyTown(proposal.targetTown, "§bAccord de non-agression actif pour " + proposal.days + " jours.");
            }
            case CEASEFIRE -> {
                war.setStatus(War.Status.PEACE_NEGOTIATION);
                notifyTown(proposal.proposerTown, "§bCessez-le-feu actif pour " + proposal.days + " jours.");
                notifyTown(proposal.targetTown, "§bCessez-le-feu actif pour " + proposal.days + " jours.");
                long duration = proposal.days * 86_400_000L;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (war.getStatus() == War.Status.PEACE_NEGOTIATION) {
                        war.setStatus(War.Status.ACTIVE);
                        notifyTown(proposal.proposerTown, "§cLe cessez-le-feu est terminé.");
                        notifyTown(proposal.targetTown, "§cLe cessez-le-feu est terminé.");
                    }
                }, duration / 50);
            }
        }
    }

    private void notifyTown(String townName, String message) {
        com.palmergames.bukkit.towny.object.Town town =
                com.palmergames.bukkit.towny.TownyAPI.getInstance().getTown(townName);
        if (town == null) return;
        for (com.palmergames.bukkit.towny.object.Resident r : town.getResidents()) {
            Player p = Bukkit.getPlayer(r.getName());
            if (p != null) p.sendMessage(message);
        }
    }

    public TreatyProposal getPendingProposal(UUID warId) { return pendingProposals.get(warId); }
}
