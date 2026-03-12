package fr.townyconflict.commands;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import fr.townyconflict.TownyConflict;
import fr.townyconflict.gui.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public class TCCommand implements CommandExecutor, TabCompleter {

    private final TownyConflict plugin;

    public TCCommand(TownyConflict plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommande réservée aux joueurs.");
            return true;
        }

        if (args.length == 0) {
            new MainMenuGUI(plugin).open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // ── Menu principal ──
            case "menu", "m" -> new MainMenuGUI(plugin).open(player);

            // ── Guerre ──
            case "war", "guerre" -> {
                if (args.length < 2) { new MainMenuGUI(plugin).open(player); return true; }
                switch (args[1].toLowerCase()) {
                    case "declare", "declarer" -> {
                        if (args.length < 3) { new WarDeclareGUI(plugin).open(player); return true; }
                        handleWarDeclare(player, args[2]);
                    }
                    case "status", "statut" -> new WarStatusGUI(plugin).open(player);
                    case "surrender", "capituler" -> handleSurrender(player);
                    default -> new MainMenuGUI(plugin).open(player);
                }
            }

            // ── Assaut ──
            case "assault", "assaut" -> {
                if (args.length < 2) { new AssaultMenuGUI(plugin).open(player); return true; }
                switch (args[1].toLowerCase()) {
                    case "start", "lancer" -> {
                        if (args.length < 3) {
                            player.sendMessage("§cUsage : /tc assault start <town>");
                            return true;
                        }
                        handleAssaultStart(player, args[2]);
                    }
                    case "join", "rejoindre" -> {
                        if (args.length < 3) {
                            player.sendMessage("§cUsage : /tc assault join <town_défenseur>");
                            return true;
                        }
                        handleAssaultJoin(player, args[2]);
                    }
                    default -> new AssaultMenuGUI(plugin).open(player);
                }
            }

            // ── Mercenaires ──
            case "merc" -> {
                if (args.length < 2) { new MercMenuGUI(plugin).open(player); return true; }
                switch (args[1].toLowerCase()) {
                    case "hire", "recruter" -> {
                        if (args.length < 4) {
                            player.sendMessage("§cUsage : /tc merc hire <joueur> <montant>");
                            return true;
                        }
                        handleMercHire(player, args[2], args[3]);
                    }
                    case "accept", "accepter" -> {
                        boolean ok = plugin.getMercenaryManager().acceptOffer(player);
                        player.sendMessage(ok ? "§aOffre acceptée !" : "§cAucune offre en attente.");
                    }
                    case "refuse", "refuser" -> {
                        boolean ok = plugin.getMercenaryManager().refuseOffer(player);
                        player.sendMessage(ok ? "§7Offre refusée." : "§cAucune offre en attente.");
                    }
                    default -> new MercMenuGUI(plugin).open(player);
                }
            }

            // ── Traités ──
            case "treaty", "traite", "traité" -> {
                if (args.length < 2) { new TreatyMenuGUI(plugin).open(player); return true; }
                switch (args[1].toLowerCase()) {
                    case "accept", "accepter" -> {
                        Resident res = TownyAPI.getInstance().getResident(player.getName());
                        if (res == null || !res.hasTown()) { player.sendMessage("§cVous n'avez pas de town."); return true; }
                        boolean ok = plugin.getTreatyManager().accept(res.getTownOrNull().getName());
                        player.sendMessage(ok ? "§aTraité accepté !" : "§cAucun traité en attente.");
                    }
                    case "refuse", "refuser" -> {
                        Resident res = TownyAPI.getInstance().getResident(player.getName());
                        if (res == null || !res.hasTown()) { player.sendMessage("§cVous n'avez pas de town."); return true; }
                        boolean ok = plugin.getTreatyManager().refuse(res.getTownOrNull().getName());
                        player.sendMessage(ok ? "§7Traité refusé." : "§cAucun traité en attente.");
                    }
                    default -> new TreatyMenuGUI(plugin).open(player);
                }
            }

            // ── Admin ──
            case "reload" -> {
                if (!player.hasPermission("townyconflict.admin")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
                    return true;
                }
                plugin.reload();
                player.sendMessage("§aTownyConflict rechargé !");
            }

            case "admin" -> {
                if (!player.hasPermission("townyconflict.admin")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
                    return true;
                }
                if (args.length < 3) { player.sendMessage("§cUsage : /tc admin <endwar|setreputation> ..."); return true; }
                handleAdmin(player, args);
            }

            default -> {
                player.sendMessage("§cSous-commande inconnue. Tapez §f/tc §cpour ouvrir le menu.");
                new MainMenuGUI(plugin).open(player);
            }
        }
        return true;
    }

    // ─────────────────────────────────────────
    //  HANDLERS
    // ─────────────────────────────────────────

    private void handleWarDeclare(Player player, String targetTown) {
        Resident res = TownyAPI.getInstance().getResident(player.getName());
        if (res == null || !res.hasTown()) { player.sendMessage("§cVous n'avez pas de town."); return; }
        if (!player.hasPermission("townyconflict.war.declare")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission")); return;
        }
        // Ouvrir le GUI avec la cible pré-remplie
        WarDeclareGUI gui = new WarDeclareGUI(plugin);
        gui.open(player);
        player.sendMessage("§7Town cible §f" + targetTown + " §7pré-sélectionnée dans le menu.");
    }

    private void handleSurrender(Player player) {
        Resident res = TownyAPI.getInstance().getResident(player.getName());
        if (res == null || !res.hasTown()) { player.sendMessage("§cVous n'avez pas de town."); return; }
        if (!player.hasPermission("townyconflict.war.surrender")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission")); return;
        }
        new WarStatusGUI(plugin).open(player);
        player.sendMessage("§7Faites un clic droit sur la guerre pour capituler.");
    }

    private void handleAssaultStart(Player player, String defenderTown) {
        Resident res = TownyAPI.getInstance().getResident(player.getName());
        if (res == null || !res.hasTown()) { player.sendMessage("§cVous n'avez pas de town."); return; }
        if (!player.hasPermission("townyconflict.assault.start")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission")); return;
        }
        String myTown = res.getTownOrNull().getName();
        var result = plugin.getAssaultManager().startAssault(player, myTown, defenderTown);
        switch (result) {
            case SUCCESS -> {}
            case NOT_IN_WAR -> player.sendMessage(plugin.getConfigManager().getMessage("not_in_war"));
            case WAR_NOT_ACTIVE -> player.sendMessage("§cLa guerre n'est pas encore active (période de grâce).");
            case ON_COOLDOWN -> player.sendMessage(plugin.getConfigManager().getMessage("cooldown").replace("{time}", "bientôt"));
            case ALREADY_IN_ASSAULT -> player.sendMessage("§cUn assaut est déjà en cours contre cette town.");
            case NOT_ENOUGH_DEFENDERS -> player.sendMessage("§cPas assez de défenseurs connectés.");
            case NO_PERMISSION -> player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
        }
    }

    private void handleAssaultJoin(Player player, String defenderTown) {
        if (!player.hasPermission("townyconflict.assault.join")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission")); return;
        }
        // Déterminer le camp selon la nation du joueur
        Resident res = TownyAPI.getInstance().getResident(player.getName());
        if (res == null || !res.hasTown()) { player.sendMessage("§cVous n'avez pas de town."); return; }

        var assault = plugin.getAssaultManager().getAssaultForTown(defenderTown);
        if (assault == null) { player.sendMessage("§cAucun assaut en cours contre cette town."); return; }

        // Essai renfort attaquant
        var result = plugin.getReinforcementManager().joinAsReinforcement(player, defenderTown, true);
        if (result == fr.townyconflict.managers.ReinforcementManager.JoinResult.NOT_IN_ALLIED_NATION) {
            // Essai côté défenseur
            result = plugin.getReinforcementManager().joinAsReinforcement(player, defenderTown, false);
        }
        switch (result) {
            case SUCCESS -> {}
            case ASSAULT_NOT_FOUND -> player.sendMessage("§cAucun assaut en cours.");
            case WAR_REINFORCEMENTS_DISABLED -> player.sendMessage("§cLes renforts sont désactivés dans cette guerre.");
            case QUOTA_FULL -> player.sendMessage("§cLe quota de renforts pour votre nation est atteint.");
            case NOT_IN_ALLIED_NATION -> player.sendMessage("§cVous n'êtes pas dans la nation requise.");
            case ALREADY_PARTICIPATING -> player.sendMessage("§cVous participez déjà à cet assaut.");
        }
    }

    private void handleMercHire(Player player, String targetName, String amountStr) {
        if (!player.hasPermission("townyconflict.merc.hire")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission")); return;
        }
        Resident res = TownyAPI.getInstance().getResident(player.getName());
        if (res == null || !res.hasTown()) { player.sendMessage("§cVous n'avez pas de town."); return; }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { player.sendMessage("§cJoueur introuvable ou hors ligne."); return; }

        double amount;
        try { amount = Double.parseDouble(amountStr); }
        catch (NumberFormatException e) { player.sendMessage("§cMontant invalide."); return; }
        if (amount <= 0) { player.sendMessage("§cLe montant doit être positif."); return; }

        String myTown = res.getTownOrNull().getName();
        var assault = plugin.getAssaultManager().getAssaultForTown(
                plugin.getWarManager().getActiveWarsForTown(myTown).stream()
                        .findFirst().map(w -> w.getOpponent(myTown)).orElse(""));
        if (assault == null) { player.sendMessage("§cAucun assaut en cours pour votre town."); return; }

        var result = plugin.getMercenaryManager().hirePlayer(player, myTown, target.getUniqueId(), amount, assault.getDefenderTown());
        switch (result) {
            case SUCCESS -> player.sendMessage("§aOffre envoyée à §f" + targetName + " §apour §f" + String.format("%.0f", amount) + "$§a !");
            case ASSAULT_NOT_FOUND -> player.sendMessage("§cAucun assaut en cours.");
            case WAR_MERC_DISABLED -> player.sendMessage("§cLes mercenaires sont désactivés dans cette guerre.");
            case TOO_MANY_MERCS -> player.sendMessage("§cVous avez atteint le max de mercenaires pour cet assaut.");
            case PLAYER_ON_COOLDOWN -> player.sendMessage("§cCe joueur est en cooldown de désertion.");
            case NOT_ENOUGH_MONEY -> player.sendMessage("§cFonds insuffisants dans la banque de votre town.");
            case LOW_REPUTATION -> player.sendMessage("§cVotre réputation est trop basse pour recruter des mercenaires.");
            case OFFER_ALREADY_PENDING -> player.sendMessage("§cUne offre est déjà en attente pour ce joueur.");
        }
    }

    private void handleAdmin(Player player, String[] args) {
        switch (args[1].toLowerCase()) {
            case "endwar" -> {
                if (args.length < 4) { player.sendMessage("§cUsage : /tc admin endwar <town1> <town2>"); return; }
                var war = plugin.getWarManager().getWarBetween(args[2], args[3]);
                if (war == null) { player.sendMessage("§cGuerre introuvable."); return; }
                plugin.getWarManager().endWar(war, args[2], fr.townyconflict.models.War.EndReason.TREATY);
                player.sendMessage("§aGuerre terminée entre §f" + args[2] + " §aet §f" + args[3]);
            }
            case "setreputation", "setrep" -> {
                if (args.length < 4) { player.sendMessage("§cUsage : /tc admin setreputation <town> <valeur>"); return; }
                try {
                    int val = Integer.parseInt(args[3]);
                    plugin.getReputationManager().setReputation(args[2], val);
                    player.sendMessage("§aRéputation de §f" + args[2] + " §adéfinie à §f" + val);
                } catch (NumberFormatException e) { player.sendMessage("§cValeur invalide."); }
            }
            default -> player.sendMessage("§cSous-commande admin inconnue.");
        }
    }

    // ─────────────────────────────────────────
    //  TAB COMPLETION
    // ─────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], "menu", "war", "assault", "merc", "treaty", "reload", "admin");
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "war", "guerre" -> filter(args[1], "declare", "status", "surrender");
                case "assault", "assaut" -> filter(args[1], "start", "join");
                case "merc" -> filter(args[1], "hire", "accept", "refuse");
                case "treaty", "traité" -> filter(args[1], "accept", "refuse");
                case "admin" -> filter(args[1], "endwar", "setreputation");
                default -> Collections.emptyList();
            };
        }
        if (args.length == 3) {
            if ((args[0].equalsIgnoreCase("assault") || args[0].equalsIgnoreCase("assaut"))
                    && (args[1].equalsIgnoreCase("start") || args[1].equalsIgnoreCase("join"))) {
                // Complétion avec les towns en guerre
                List<String> towns = new ArrayList<>();
                TownyAPI.getInstance().getTowns().forEach(t -> towns.add(t.getName()));
                return filter(args[2], towns.toArray(new String[0]));
            }
            if (args[0].equalsIgnoreCase("merc") && args[1].equalsIgnoreCase("hire")) {
                List<String> names = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                return filter(args[2], names.toArray(new String[0]));
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(String input, String... options) {
        List<String> result = new ArrayList<>();
        for (String opt : options) if (opt.toLowerCase().startsWith(input.toLowerCase())) result.add(opt);
        return result;
    }
}
