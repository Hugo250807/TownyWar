package fr.townyconflict.gui;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import fr.townyconflict.TownyConflict;
import fr.townyconflict.models.War;
import fr.townyconflict.utils.GuiUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import java.util.ArrayList;
import java.util.List;

// ══════════════════════════════════════════════
//  STATUT DES GUERRES
// ══════════════════════════════════════════════
class WarStatusGUI implements Listener {
    private final TownyConflict plugin;
    public WarStatusGUI(TownyConflict plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text(plugin.getConfigManager().getGuiTitle("war_status")));
        for (int i = 0; i < 54; i++) inv.setItem(i, GuiUtils.filler());

        Resident res = TownyAPI.getInstance().getResident(player.getName());
        Town town = res != null && res.hasTown() ? res.getTownOrNull() : null;

        if (town == null) { player.sendMessage("§cVous n'avez pas de town."); return; }

        List<War> wars = plugin.getWarManager().getActiveWarsForTown(town.getName());
        int slot = 10;
        for (War war : wars) {
            if (slot >= 44) break;
            String opponent = war.getOpponent(town.getName());
            boolean isAttacker = war.isAttacker(town.getName());

            List<String> lore = new ArrayList<>();
            lore.add("&7Rôle : " + (isAttacker ? "&cAttaquant" : "&aDefenseur"));
            lore.add("&7Statut : &f" + war.getStatus().name());
            lore.add("&7Condition : &f" + war.getVictoryConditionType() + " (" + war.getVictoryConditionValue() + ")");
            lore.add("");
            lore.add("&7Points guerre : &f" + war.getWarPoints(town.getName()) +
                    " &8vs &f" + war.getWarPoints(opponent));
            lore.add("&7Assauts gagnés : &f" + war.getAttackerAssaultWins() +
                    " &8vs &f" + war.getDefenderAssaultWins());
            lore.add("");
            lore.add("&7Renforts : " + (war.isAllowNationReinforcements() ? "&aOui" : "&cNon"));
            lore.add("&7Mercenaires : " + (war.isAllowMercenaries() ? "&aOui" : "&cNon"));
            lore.add("");
            lore.add("&7Récompenses :");
            war.getRewards().forEach(r -> lore.add("  &8- &f" + r.describe()));
            lore.add("");
            if (war.getDaysElapsed() >= plugin.getConfigManager().getSurrenderMinDays()) {
                lore.add("&cCliquez pour capituler");
            } else {
                lore.add("&7Capitulation disponible dans &f" +
                        (plugin.getConfigManager().getSurrenderMinDays() - war.getDaysElapsed()) + " jours");
            }

            inv.setItem(slot, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("war_icon"),
                    "&cvs &f" + opponent, lore));
            slot += 2;
        }

        if (wars.isEmpty()) {
            inv.setItem(22, GuiUtils.item(org.bukkit.Material.WHITE_BANNER, "&7Aucune guerre active",
                    "&7Votre town est en paix."));
        }

        inv.setItem(49, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("back"), "&7← Retour"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());
        if (!title.contains("Statut")) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        String name = event.getCurrentItem().getItemMeta() != null ?
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(event.getCurrentItem().getItemMeta().displayName()) : "";

        if (name.contains("Retour")) { new MainMenuGUI(plugin).open(player); return; }

        // Capitulation au clic droit sur une guerre
        if (name.startsWith("vs ") && event.isRightClick()) {
            String opponent = name.replace("vs ", "").trim();
            Resident res = TownyAPI.getInstance().getResident(player.getName());
            if (res == null || !res.hasTown()) return;
            War war = plugin.getWarManager().getWarBetween(res.getTownOrNull().getName(), opponent);
            if (war != null && war.getDaysElapsed() >= plugin.getConfigManager().getSurrenderMinDays()) {
                plugin.getWarManager().endWar(war, opponent, War.EndReason.SURRENDER);
                player.closeInventory();
                player.sendMessage("§cVous avez capitulé.");
            }
        }
    }
}

// ══════════════════════════════════════════════
//  MENU ASSAUT
// ══════════════════════════════════════════════
class AssaultMenuGUI implements Listener {
    private final TownyConflict plugin;
    public AssaultMenuGUI(TownyConflict plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45,
                net.kyori.adventure.text.Component.text(plugin.getConfigManager().getGuiTitle("assault_menu")));
        for (int i = 0; i < 45; i++) inv.setItem(i, GuiUtils.filler());

        Resident res = TownyAPI.getInstance().getResident(player.getName());
        Town town = res != null && res.hasTown() ? res.getTownOrNull() : null;
        if (town == null) { player.sendMessage("§cVous n'avez pas de town."); return; }

        List<War> wars = plugin.getWarManager().getActiveWarsForTown(town.getName());
        int slot = 10;
        for (War war : wars) {
            if (!war.isActive()) continue;
            if (slot >= 35) break;
            String opponent = war.getOpponent(town.getName());
            boolean isAttacker = war.isAttacker(town.getName());

            // Vérif cooldown
            long cooldownMs = plugin.getConfigManager().getAssaultCooldownHours() * 3600_000L;
            long lastEnd = war.getLastAssaultEndTime();
            long remaining = lastEnd > 0 && !war.isCounterAttackAvailable() ?
                    Math.max(0, (lastEnd + cooldownMs) - System.currentTimeMillis()) : 0;
            boolean canAssault = isAttacker && remaining == 0;

            List<String> lore = new ArrayList<>();
            lore.add("&7Adversaire : &f" + opponent);
            lore.add("&7Votre rôle : " + (isAttacker ? "&cAttaquant" : "&aDefenseur"));
            if (remaining > 0) lore.add("&c⏳ Cooldown : " + GuiUtils.formatTime(remaining));
            else if (!isAttacker) lore.add("&7Défendez votre territoire !");
            else lore.add("&aVous pouvez lancer un assaut !");
            lore.add("");
            if (canAssault) lore.add("&eCliquez pour lancer l'assaut");
            else if (!isAttacker) lore.add("&7Rejoignez la défense si un assaut est lancé.");

            inv.setItem(slot, canAssault ?
                    GuiUtils.glowing(plugin.getConfigManager().getGuiMaterial("assault_icon"),
                            "&c⚔ Attaquer &f" + opponent, lore) :
                    GuiUtils.item(plugin.getConfigManager().getGuiMaterial("assault_icon"),
                            "&7Guerre vs &f" + opponent, lore));
            slot += 2;
        }

        if (wars.stream().noneMatch(War::isActive)) {
            inv.setItem(22, GuiUtils.item(org.bukkit.Material.BARRIER, "&cAucune guerre active"));
        }

        inv.setItem(40, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("back"), "&7← Retour"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());
        if (!title.contains("assaut") && !title.contains("Assaut")) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        String name = event.getCurrentItem().getItemMeta() != null ?
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(event.getCurrentItem().getItemMeta().displayName()) : "";

        if (name.contains("Retour")) { new MainMenuGUI(plugin).open(player); return; }

        if (name.contains("Attaquer")) {
            String opponent = name.replace("⚔ Attaquer", "").trim();
            Resident res = TownyAPI.getInstance().getResident(player.getName());
            if (res == null || !res.hasTown()) return;
            String myTown = res.getTownOrNull().getName();
            player.closeInventory();
            var result = plugin.getAssaultManager().startAssault(player, myTown, opponent);
            switch (result) {
                case SUCCESS -> {} // Message déjà broadcast
                case ON_COOLDOWN -> player.sendMessage(plugin.getConfigManager().getMessage("cooldown")
                        .replace("{time}", "bientôt"));
                case NOT_ENOUGH_DEFENDERS -> player.sendMessage("§cPas assez de défenseurs connectés (" +
                        plugin.getConfigManager().getMinDefendersOnline() + " minimum).");
                case ALREADY_IN_ASSAULT -> player.sendMessage("§cUn assaut est déjà en cours contre cette town.");
                default -> player.sendMessage("§cImpossible de lancer l'assaut.");
            }
        }
    }
}

// ══════════════════════════════════════════════
//  MENU MERCENAIRES
// ══════════════════════════════════════════════
class MercMenuGUI implements Listener {
    private final TownyConflict plugin;
    public MercMenuGUI(TownyConflict plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36,
                net.kyori.adventure.text.Component.text(plugin.getConfigManager().getGuiTitle("merc_menu")));
        for (int i = 0; i < 36; i++) inv.setItem(i, GuiUtils.filler());

        // Offre en attente pour ce joueur ?
        var offer = plugin.getMercenaryManager().getPendingOffer(player.getUniqueId());
        if (offer != null) {
            inv.setItem(13, GuiUtils.glowing(plugin.getConfigManager().getGuiMaterial("merc_icon"),
                    "&6⚔ Offre de " + offer.hiringTown,
                    "&7Montant total : &f" + String.format("%.0f", offer.totalAmount) + "$",
                    "&7Camp : " + (offer.forAttacker ? "&cAttaquants" : "&aDefenseurs"),
                    "", "&aCliquez pour ACCEPTER"));
            inv.setItem(15, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("cancel"),
                    "&cRefuser l'offre", "&7Vous refuserez l'offre de §f" + offer.hiringTown));
        } else if (plugin.getMercenaryManager().isOnDesertionCooldown(player.getUniqueId())) {
            long remaining = plugin.getMercenaryManager().getDesertionCooldownRemaining(player.getUniqueId());
            inv.setItem(13, GuiUtils.item(org.bukkit.Material.BARRIER, "&cCooldown de désertion",
                    "&7Vous ne pouvez pas être mercenaire pendant encore",
                    "&f" + GuiUtils.formatTime(remaining)));
        } else {
            inv.setItem(13, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("merc_icon"),
                    "&7Aucune offre en attente",
                    "&7Attendez qu'une town vous recrute via",
                    "&f/tc merc hire <joueur> <montant>"));
        }

        // Infos sur le système
        inv.setItem(4, GuiUtils.item(org.bukkit.Material.PAPER, "&e📋 Système Mercenaire",
                "&7Max par assaut : &f" + plugin.getConfigManager().getMercMaxPerTeam(),
                "&7Acompte : &f" + plugin.getConfigManager().getMercUpfrontPaymentPercent() + "% à la signature",
                "&7Reste : versé à la victoire",
                "&7Désertion : cooldown de &f" + plugin.getConfigManager().getMercDesertionCooldownDays() + " jours"));

        inv.setItem(31, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("back"), "&7← Retour"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());
        if (!title.contains("mercenaire") && !title.contains("Mercenaire")) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        String name = event.getCurrentItem().getItemMeta() != null ?
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(event.getCurrentItem().getItemMeta().displayName()) : "";

        if (name.contains("Retour")) { new MainMenuGUI(plugin).open(player); return; }
        if (name.contains("Offre de")) { plugin.getMercenaryManager().acceptOffer(player); player.closeInventory(); }
        if (name.contains("Refuser")) { plugin.getMercenaryManager().refuseOffer(player); player.closeInventory(); }
    }
}

// ══════════════════════════════════════════════
//  MENU TRAITÉS
// ══════════════════════════════════════════════
class TreatyMenuGUI implements Listener {
    private final TownyConflict plugin;
    public TreatyMenuGUI(TownyConflict plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36,
                net.kyori.adventure.text.Component.text(plugin.getConfigManager().getGuiTitle("treaty_menu")));
        for (int i = 0; i < 36; i++) inv.setItem(i, GuiUtils.filler());

        Resident res = TownyAPI.getInstance().getResident(player.getName());
        Town town = res != null && res.hasTown() ? res.getTownOrNull() : null;
        if (town == null) { player.sendMessage("§cVous n'avez pas de town."); return; }

        List<War> wars = plugin.getWarManager().getActiveWarsForTown(town.getName());

        if (wars.isEmpty()) {
            inv.setItem(13, GuiUtils.item(org.bukkit.Material.BARRIER, "&cAucune guerre active"));
        } else {
            int slot = 10;
            for (War war : wars) {
                if (!war.isActive()) continue;
                String opponent = war.getOpponent(town.getName());
                long minDays = plugin.getConfigManager().getTreatyAvailableAfterDays();
                boolean canPropose = war.getDaysElapsed() >= minDays;

                List<String> lore = new ArrayList<>();
                lore.add("&7Guerre vs &f" + opponent);
                lore.add("&7Jours écoulés : &f" + war.getDaysElapsed());
                if (!canPropose) {
                    lore.add("&c⏳ Disponible après &f" + minDays + " jours de guerre");
                } else {
                    lore.add("&eCliquez pour proposer un traité");
                }

                inv.setItem(slot, canPropose ?
                        GuiUtils.glowing(plugin.getConfigManager().getGuiMaterial("treaty_icon"),
                                "&b🕊 " + opponent, lore) :
                        GuiUtils.item(plugin.getConfigManager().getGuiMaterial("treaty_icon"),
                                "&7vs " + opponent, lore));
                slot += 2;
            }
        }

        // Proposition en attente ?
        for (War war : wars) {
            var proposal = plugin.getTreatyManager().getPendingProposal(war.getId());
            if (proposal != null && proposal.targetTown.equalsIgnoreCase(town.getName())) {
                inv.setItem(31, GuiUtils.glowing(org.bukkit.Material.WHITE_BANNER,
                        "&a⚡ Traité en attente de &f" + proposal.proposerTown,
                        "&7Type : &f" + proposal.type.name(),
                        "", "&aCliquez pour ACCEPTER"));
                inv.setItem(29, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("cancel"),
                        "&cRefuser le traité"));
                break;
            }
        }

        inv.setItem(35, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("back"), "&7← Retour"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());
        if (!title.contains("raité")) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        String name = event.getCurrentItem().getItemMeta() != null ?
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(event.getCurrentItem().getItemMeta().displayName()) : "";

        Resident res = TownyAPI.getInstance().getResident(player.getName());
        if (res == null || !res.hasTown()) return;
        String myTown = res.getTownOrNull().getName();

        if (name.contains("Retour")) { new MainMenuGUI(plugin).open(player); return; }
        if (name.contains("⚡ Traité en attente")) {
            plugin.getTreatyManager().accept(myTown);
            player.closeInventory();
            player.sendMessage("§aVous avez accepté le traité.");
            return;
        }
        if (name.contains("Refuser")) {
            plugin.getTreatyManager().refuse(myTown);
            player.closeInventory();
            player.sendMessage("§7Vous avez refusé le traité.");
            return;
        }
        // Proposer un traité (menu de sélection de type)
        if (name.startsWith("🕊") || name.startsWith("vs")) {
            String opponent = name.replace("🕊 ", "").replace("vs ", "").trim();
            openTreatyTypeSelection(player, myTown, opponent);
        }
    }

    private void openTreatyTypeSelection(Player player, String myTown, String opponent) {
        Inventory inv = Bukkit.createInventory(null, 27,
                net.kyori.adventure.text.Component.text("&8Proposer un traité à " + opponent));
        for (int i = 0; i < 27; i++) inv.setItem(i, GuiUtils.filler());

        if (plugin.getConfigManager().isTreatyTypeEnabled("peace")) {
            inv.setItem(11, GuiUtils.item(org.bukkit.Material.WHITE_BANNER, "&bTraité de paix",
                    "&7Mettre fin à la guerre.", "&eCliquez pour proposer"));
        }
        if (plugin.getConfigManager().isTreatyTypeEnabled("non_aggression")) {
            inv.setItem(13, GuiUtils.item(org.bukkit.Material.WHITE_WOOL, "&bNon-agression",
                    "&7Aucun assaut pendant une durée définie.", "&eCliquez pour proposer"));
        }
        if (plugin.getConfigManager().isTreatyTypeEnabled("ceasefire")) {
            inv.setItem(15, GuiUtils.item(org.bukkit.Material.CLOCK, "&bCessez-le-feu",
                    "&7Pause temporaire dans le conflit.", "&eCliquez pour proposer"));
        }

        inv.setItem(22, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("back"), "&7← Retour"));
        player.openInventory(inv);

        // Listener temporaire pour ce sous-menu
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onTypeClick(InventoryClickEvent e) {
                if (!(e.getWhoClicked().equals(player))) return;
                String t = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(e.getView().title());
                if (!t.contains("Proposer un traité")) return;
                e.setCancelled(true);
                if (e.getCurrentItem() == null) return;
                String n = e.getCurrentItem().getItemMeta() != null ?
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText().serialize(e.getCurrentItem().getItemMeta().displayName()) : "";

                if (n.contains("Retour")) { open(player); return; }
                fr.townyconflict.managers.TreatyManager.TreatyType type = null;
                if (n.contains("paix")) type = fr.townyconflict.managers.TreatyManager.TreatyType.PEACE;
                else if (n.contains("agression")) type = fr.townyconflict.managers.TreatyManager.TreatyType.NON_AGGRESSION;
                else if (n.contains("feu")) type = fr.townyconflict.managers.TreatyManager.TreatyType.CEASEFIRE;

                if (type != null) {
                    var result = plugin.getTreatyManager().propose(myTown, opponent, type, 7);
                    player.closeInventory();
                    player.sendMessage(result == fr.townyconflict.managers.TreatyManager.ProposeResult.SUCCESS ?
                            "§bProposition de traité envoyée à §f" + opponent + "§b !" :
                            "§cImpossible de proposer ce traité : " + result.name());
                }
            }
        }, plugin);
    }
}
