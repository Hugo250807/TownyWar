package fr.townyconflict.gui;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import fr.townyconflict.TownyConflict;
import fr.townyconflict.managers.ConfigManager;
import fr.townyconflict.models.War;
import fr.townyconflict.models.WarReward;
import fr.townyconflict.utils.GuiUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import java.util.*;

/**
 * GUI de déclaration de guerre en 3 étapes :
 * 1. Choisir la town cible (liste des towns connues)
 * 2. Configurer les options (renforts, mercs, alliés)
 * 3. Choisir condition de victoire + récompenses → Confirmer
 */
public class WarDeclareGUI implements Listener {

    private final TownyConflict plugin;

    // État de configuration par joueur
    private final Map<UUID, WarConfig> playerConfigs = new HashMap<>();

    public static class WarConfig {
        public String targetTown;
        public boolean allowReinforcements;
        public boolean allowMercenaries;
        public boolean allowAllied;
        public String victoryType;
        public int victoryValue;
        public List<WarReward> rewards = new ArrayList<>();
        public int step = 1; // 1=options, 2=victoire, 3=récompenses, 4=confirm

        public WarConfig(ConfigManager cfg) {
            allowReinforcements = cfg.getWarOptionDefault("allow_nation_reinforcements");
            allowMercenaries = cfg.getWarOptionDefault("allow_mercenaries");
            allowAllied = cfg.getWarOptionDefault("allow_allied_nations");
            victoryType = "assault_wins";
            victoryValue = cfg.getVictoryConditionDefault("assault_wins");
        }
    }

    public WarDeclareGUI(TownyConflict plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Resident res = TownyAPI.getInstance().getResident(player.getName());
        if (res == null || !res.hasTown()) {
            player.sendMessage(plugin.getConfigManager().getMessage("not_mayor"));
            return;
        }

        WarConfig config = playerConfigs.computeIfAbsent(player.getUniqueId(),
                k -> new WarConfig(plugin.getConfigManager()));
        config.step = 1;
        openOptionsStep(player, config);
    }

    // ─────────────────────────────────────────
    //  ÉTAPE 1 : OPTIONS
    // ─────────────────────────────────────────

    private void openOptionsStep(Player player, WarConfig config) {
        Inventory inv = Bukkit.createInventory(null, 45,
                net.kyori.adventure.text.Component.text(
                        plugin.getConfigManager().getGuiTitle("war_options")));
        for (int i = 0; i < 45; i++) inv.setItem(i, GuiUtils.filler());

        ConfigManager cfg = plugin.getConfigManager();

        // Titre de l'étape
        inv.setItem(4, GuiUtils.item(org.bukkit.Material.PAPER, "&e📋 Étape 1/3 — Options de guerre",
                "&7Configurez les règles de ce conflit."));

        // Town cible
        String targetLabel = config.targetTown != null ? "&aCible : &f" + config.targetTown : "&cAucune cible";
        inv.setItem(13, GuiUtils.item(org.bukkit.Material.PLAYER_HEAD, "&6🎯 Choisir la cible",
                targetLabel, "", "&eCliquez pour choisir la town adverse"));

        // Renforts nationaux
        if (cfg.isWarOptionEnabled("allow_nation_reinforcements")) {
            inv.setItem(20, GuiUtils.item(
                    config.allowReinforcements ? cfg.getGuiMaterial("enabled") : cfg.getGuiMaterial("disabled"),
                    "&eRenforts nationaux",
                    config.allowReinforcements ? "&aActivés" : "&cDésactivés",
                    "", "&7Les membres de la nation peuvent rejoindre l'assaut.",
                    "&eCliquez pour basculer"));
        }

        // Mercenaires
        if (cfg.isWarOptionEnabled("allow_mercenaries")) {
            inv.setItem(22, GuiUtils.item(
                    config.allowMercenaries ? cfg.getGuiMaterial("enabled") : cfg.getGuiMaterial("disabled"),
                    "&eMercenaires",
                    config.allowMercenaries ? "&aAutorisés" : "&cInterdits",
                    "", "&7Joueurs neutres recrutables contre de l'argent.",
                    "&eCliquez pour basculer"));
        }

        // Nations alliées
        if (cfg.isWarOptionEnabled("allow_allied_nations")) {
            inv.setItem(24, GuiUtils.item(
                    config.allowAllied ? cfg.getGuiMaterial("enabled") : cfg.getGuiMaterial("disabled"),
                    "&eNations alliées",
                    config.allowAllied ? "&aAutorisées" : "&cInterdites",
                    "", "&7Les nations alliées peuvent intervenir.",
                    "&eCliquez pour basculer"));
        }

        // Navigation
        inv.setItem(38, GuiUtils.item(cfg.getGuiMaterial("back"), "&7← Retour", "&7Menu principal"));
        inv.setItem(40, GuiUtils.item(cfg.getGuiMaterial("confirm"), "&a→ Suivant : Condition de victoire",
                "&7Étape 2/3", config.targetTown != null ? "&aOK" : "&cChoisissez une cible d'abord"));

        player.openInventory(inv);
    }

    // ─────────────────────────────────────────
    //  ÉTAPE 2 : CONDITION DE VICTOIRE
    // ─────────────────────────────────────────

    private void openVictoryStep(Player player, WarConfig config) {
        Inventory inv = Bukkit.createInventory(null, 45,
                net.kyori.adventure.text.Component.text(
                        plugin.getConfigManager().getGuiTitle("victory_conditions")));
        for (int i = 0; i < 45; i++) inv.setItem(i, GuiUtils.filler());

        ConfigManager cfg = plugin.getConfigManager();

        inv.setItem(4, GuiUtils.item(org.bukkit.Material.PAPER, "&e📋 Étape 2/3 — Condition de victoire",
                "&7Choisissez comment gagner la guerre."));

        // Victoires d'assauts
        if (cfg.isVictoryConditionEnabled("assault_wins")) {
            boolean selected = "assault_wins".equals(config.victoryType);
            inv.setItem(19, selected ?
                    GuiUtils.glowing(cfg.getGuiMaterial("victory_wins"), "&a✔ Victoires d'assauts",
                            "&7Gagner &f" + config.victoryValue + " &7assauts.",
                            "", "&7Min: " + cfg.getVictoryConditionMin("assault_wins") +
                                    " | Max: " + cfg.getVictoryConditionMax("assault_wins"),
                            "&eCliquez pour sélectionner / modifier") :
                    GuiUtils.item(cfg.getGuiMaterial("victory_wins"), "&7Victoires d'assauts",
                            "&7Gagner X assauts.", "", "&eCliquez pour sélectionner"));
        }

        // Points de guerre
        if (cfg.isVictoryConditionEnabled("war_points")) {
            boolean selected = "war_points".equals(config.victoryType);
            inv.setItem(22, selected ?
                    GuiUtils.glowing(cfg.getGuiMaterial("victory_points"), "&a✔ Points de guerre",
                            "&7Accumuler &f" + config.victoryValue + " &7points.",
                            "", "&7Min: " + cfg.getVictoryConditionMin("war_points") +
                                    " | Max: " + cfg.getVictoryConditionMax("war_points"),
                            "&eCliquez pour sélectionner / modifier") :
                    GuiUtils.item(cfg.getGuiMaterial("victory_points"), "&7Points de guerre",
                            "&7Accumuler X points de guerre.", "", "&eCliquez pour sélectionner"));
        }

        // Limite de temps
        if (cfg.isVictoryConditionEnabled("time_limit")) {
            boolean selected = "time_limit".equals(config.victoryType);
            inv.setItem(25, selected ?
                    GuiUtils.glowing(cfg.getGuiMaterial("victory_time"), "&a✔ Limite de temps",
                            "&7Durée : &f" + config.victoryValue + " &7jours.",
                            "", "&7Min: " + cfg.getVictoryConditionMin("time_limit") +
                                    " | Max: " + cfg.getVictoryConditionMax("time_limit"),
                            "&eCliquez pour sélectionner / modifier") :
                    GuiUtils.item(cfg.getGuiMaterial("victory_time"), "&7Limite de temps",
                            "&7X jours, le camp avec le plus de points gagne.", "", "&eCliquez pour sélectionner"));
        }

        // Navigation
        inv.setItem(36, GuiUtils.item(cfg.getGuiMaterial("back"), "&7← Retour", "&7Étape 1"));
        inv.setItem(40, GuiUtils.item(cfg.getGuiMaterial("confirm"), "&a→ Suivant : Récompenses",
                "&7Étape 3/3", "&7Condition : &f" + config.victoryType + " (" + config.victoryValue + ")"));

        player.openInventory(inv);
    }

    // ─────────────────────────────────────────
    //  ÉTAPE 3 : RÉCOMPENSES
    // ─────────────────────────────────────────

    private void openRewardsStep(Player player, WarConfig config) {
        Inventory inv = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text(
                        plugin.getConfigManager().getGuiTitle("rewards")));
        for (int i = 0; i < 54; i++) inv.setItem(i, GuiUtils.filler());

        ConfigManager cfg = plugin.getConfigManager();

        inv.setItem(4, GuiUtils.item(org.bukkit.Material.PAPER, "&e📋 Étape 3/3 — Récompenses",
                "&7Choisissez ce que le vainqueur obtient."));

        // Argent
        if (cfg.isRewardEnabled("money")) {
            boolean active = config.rewards.stream().anyMatch(r -> r.getType() == WarReward.Type.MONEY);
            double pct = cfg.getMoneyRewardDefaultPercent();
            inv.setItem(20, GuiUtils.item(cfg.getGuiMaterial("money_reward"),
                    active ? "&a✔ Argent (" + pct + "%)" : "&7Argent",
                    "&7" + pct + "% de la banque adverse",
                    "&7Max : " + cfg.getMoneyRewardMaxPercent() + "%",
                    "", active ? "&cCliquez pour retirer" : "&eCliquez pour ajouter"));
        }

        // Claims
        if (cfg.isRewardEnabled("claims")) {
            boolean active = config.rewards.stream().anyMatch(r -> r.getType() == WarReward.Type.CLAIMS);
            int plots = 3;
            inv.setItem(22, GuiUtils.item(cfg.getGuiMaterial("claims_reward"),
                    active ? "&a✔ Plots (" + plots + ")" : "&7Revendication de plots",
                    "&7" + plots + " plots à la frontière",
                    "&7Max : " + cfg.getClaimsRewardMaxPlots(),
                    "", active ? "&cCliquez pour retirer" : "&eCliquez pour ajouter"));
        }

        // Vassalisation
        if (cfg.isRewardEnabled("vassalization")) {
            boolean active = config.rewards.stream().anyMatch(r -> r.getType() == WarReward.Type.VASSALIZATION);
            inv.setItem(24, GuiUtils.item(cfg.getGuiMaterial("vassalization_reward"),
                    active ? "&a✔ Vassalisation (" + cfg.getVassalizationDays() + "j)" : "&7Vassalisation",
                    "&7La town vaincue rejoint votre nation",
                    "&7pendant " + cfg.getVassalizationDays() + " jours.",
                    "", active ? "&cCliquez pour retirer" : "&eCliquez pour ajouter"));
        }

        // Non-agression
        if (cfg.isRewardEnabled("non_aggression")) {
            boolean active = config.rewards.stream().anyMatch(r -> r.getType() == WarReward.Type.NON_AGGRESSION);
            int days = cfg.getNonAggressionDefaultDays();
            inv.setItem(30, GuiUtils.item(cfg.getGuiMaterial("non_aggression_reward"),
                    active ? "&a✔ Non-agression (" + days + "j)" : "&7Non-agression imposée",
                    "&7Interdit à la town vaincue de vous",
                    "&7redéclarer la guerre pendant " + days + " jours.",
                    "", active ? "&cCliquez pour retirer" : "&eCliquez pour ajouter"));
        }

        // Récapitulatif
        List<String> recap = new ArrayList<>();
        recap.add("&7Cible : &f" + (config.targetTown != null ? config.targetTown : "&cN/A"));
        recap.add("&7Condition : &f" + config.victoryType + " (" + config.victoryValue + ")");
        recap.add("&7Renforts : " + (config.allowReinforcements ? "&aOui" : "&cNon"));
        recap.add("&7Mercenaires : " + (config.allowMercenaries ? "&aOui" : "&cNon"));
        recap.add("&7Récompenses : &f" + config.rewards.size());
        recap.add("");
        double cost = plugin.getConfigManager().getWarDeclarationCost();
        recap.add("&6Coût de déclaration : &f" + String.format("%.0f", cost) + "$");
        recap.add("");
        recap.add("&aCliquez pour déclarer la guerre !");
        inv.setItem(49, GuiUtils.glowing(cfg.getGuiMaterial("confirm"), "&a✔ Confirmer & Déclarer", recap));

        // Retour
        inv.setItem(45, GuiUtils.item(cfg.getGuiMaterial("back"), "&7← Retour", "&7Étape 2"));

        player.openInventory(inv);
    }

    // ─────────────────────────────────────────
    //  SÉLECTION DE TOWN CIBLE
    // ─────────────────────────────────────────

    private void openTownSelectionGUI(Player player, WarConfig config) {
        Inventory inv = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text("&8Choisir la town cible"));
        for (int i = 0; i < 54; i++) inv.setItem(i, GuiUtils.filler());

        Resident res = TownyAPI.getInstance().getResident(player.getName());
        Town myTown = res != null && res.hasTown() ? res.getTownOrNull() : null;

        List<Town> towns = new ArrayList<>(TownyAPI.getInstance().getTowns());
        int slot = 10;
        for (Town town : towns) {
            if (slot >= 44 || slot == 17 || slot == 26 || slot == 35) {
                slot++;
                continue;
            }
            if (myTown != null && town.getName().equalsIgnoreCase(myTown.getName())) continue;
            if (!plugin.getConfigManager().allowCivilWar() && myTown != null
                    && myTown.hasNation() && town.hasNation()
                    && myTown.getNationOrNull() != null && town.getNationOrNull() != null
                    && myTown.getNationOrNull().getName().equals(town.getNationOrNull().getName())) continue;

            List<String> lore = new ArrayList<>();
            lore.add("&7Résidents : &f" + town.getNumResidents());
            lore.add("&7Nation : &f" + (town.hasNation() && town.getNationOrNull() != null ?
                    town.getNationOrNull().getName() : "Aucune"));
            int rep = plugin.getReputationManager().getReputation(town.getName());
            lore.add("&7Réputation : " + plugin.getReputationManager().getReputationLabel(rep));
            boolean inWar = !plugin.getWarManager().getActiveWarsForTown(town.getName()).isEmpty();
            if (inWar) lore.add("&c⚔ En guerre");
            lore.add("");
            lore.add("&eCliquez pour cibler &f" + town.getName());

            inv.setItem(slot, GuiUtils.item(org.bukkit.Material.PLAYER_HEAD, "&f" + town.getName(), lore));
            slot++;
        }

        inv.setItem(49, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("back"), "&7← Retour"));
        player.openInventory(inv);
    }

    // ─────────────────────────────────────────
    //  CLICK HANDLER
    // ─────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());
        WarConfig config = playerConfigs.get(player.getUniqueId());
        if (config == null) return;

        event.setCancelled(true);

        String itemName = event.getCurrentItem().getItemMeta() != null ?
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(event.getCurrentItem().getItemMeta().displayName()) : "";

        // ── Sélection de town ──
        if (title.contains("Choisir la town cible")) {
            if (itemName.contains("Retour")) { config.step = 1; openOptionsStep(player, config); return; }
            if (!itemName.isBlank() && !itemName.equals(" ")) {
                config.targetTown = itemName.trim();
                config.step = 1;
                openOptionsStep(player, config);
            }
            return;
        }

        // ── Options ──
        if (title.contains("Options")) {
            if (itemName.contains("Retour")) { new MainMenuGUI(plugin).open(player); return; }
            if (itemName.contains("Choisir la cible")) { openTownSelectionGUI(player, config); return; }
            if (itemName.contains("Renforts")) { config.allowReinforcements = !config.allowReinforcements; openOptionsStep(player, config); return; }
            if (itemName.contains("Mercenaires")) { config.allowMercenaries = !config.allowMercenaries; openOptionsStep(player, config); return; }
            if (itemName.contains("Nations alliées")) { config.allowAllied = !config.allowAllied; openOptionsStep(player, config); return; }
            if (itemName.contains("Suivant") && config.targetTown != null) { config.step = 2; openVictoryStep(player, config); }
            return;
        }

        // ── Victoire ──
        if (title.contains("Condition")) {
            if (itemName.contains("Retour")) { config.step = 1; openOptionsStep(player, config); return; }
            if (itemName.contains("Victoires d'assauts")) {
                config.victoryType = "assault_wins";
                config.victoryValue = adjustValue(config.victoryValue, event.isRightClick(),
                        plugin.getConfigManager().getVictoryConditionMin("assault_wins"),
                        plugin.getConfigManager().getVictoryConditionMax("assault_wins"),
                        plugin.getConfigManager().getVictoryConditionDefault("assault_wins"));
                openVictoryStep(player, config); return;
            }
            if (itemName.contains("Points de guerre")) {
                config.victoryType = "war_points";
                config.victoryValue = adjustValue(config.victoryValue, event.isRightClick(),
                        plugin.getConfigManager().getVictoryConditionMin("war_points"),
                        plugin.getConfigManager().getVictoryConditionMax("war_points"),
                        plugin.getConfigManager().getVictoryConditionDefault("war_points"));
                openVictoryStep(player, config); return;
            }
            if (itemName.contains("Limite de temps")) {
                config.victoryType = "time_limit";
                config.victoryValue = adjustValue(config.victoryValue, event.isRightClick(),
                        plugin.getConfigManager().getVictoryConditionMin("time_limit"),
                        plugin.getConfigManager().getVictoryConditionMax("time_limit"),
                        plugin.getConfigManager().getVictoryConditionDefault("time_limit"));
                openVictoryStep(player, config); return;
            }
            if (itemName.contains("Suivant")) { config.step = 3; openRewardsStep(player, config); }
            return;
        }

        // ── Récompenses ──
        if (title.contains("Récompenses")) {
            if (itemName.contains("Retour")) { config.step = 2; openVictoryStep(player, config); return; }
            toggleReward(config, itemName);
            if (itemName.contains("Confirmer")) {
                confirmAndDeclare(player, config);
                return;
            }
            openRewardsStep(player, config);
        }
    }

    private int adjustValue(int current, boolean decrease, int min, int max, int def) {
        if (decrease) return Math.max(min, current - 1);
        return Math.min(max, current + 1);
    }

    private void toggleReward(WarConfig config, String itemName) {
        ConfigManager cfg = plugin.getConfigManager();
        if (itemName.contains("Argent")) {
            toggleRewardType(config, WarReward.Type.MONEY,
                    WarReward.money(cfg.getMoneyRewardDefaultPercent()));
        } else if (itemName.contains("Plots") || itemName.contains("Revendication")) {
            toggleRewardType(config, WarReward.Type.CLAIMS,
                    WarReward.claims(3));
        } else if (itemName.contains("Vassalisation")) {
            toggleRewardType(config, WarReward.Type.VASSALIZATION,
                    WarReward.vassalization(cfg.getVassalizationDays()));
        } else if (itemName.contains("Non-agression")) {
            toggleRewardType(config, WarReward.Type.NON_AGGRESSION,
                    WarReward.nonAggression(cfg.getNonAggressionDefaultDays()));
        }
    }

    private void toggleRewardType(WarConfig config, WarReward.Type type, WarReward newReward) {
        boolean removed = config.rewards.removeIf(r -> r.getType() == type);
        if (!removed) config.rewards.add(newReward);
    }

    private void confirmAndDeclare(Player player, WarConfig config) {
        if (config.targetTown == null) {
            player.sendMessage("§cChoisissez une town cible !");
            return;
        }
        Resident res = TownyAPI.getInstance().getResident(player.getName());
        if (res == null || !res.hasTown()) return;
        String myTown = res.getTownOrNull().getName();

        var result = plugin.getWarManager().declareWar(player, myTown, config.targetTown);
        player.closeInventory();

        switch (result) {
            case SUCCESS -> {
                War war = plugin.getWarManager().getWarBetween(myTown, config.targetTown);
                if (war != null) {
                    war.setAllowNationReinforcements(config.allowReinforcements);
                    war.setAllowMercenaries(config.allowMercenaries);
                    war.setAllowAlliedNations(config.allowAllied);
                    war.setVictoryConditionType(config.victoryType);
                    war.setVictoryConditionValue(config.victoryValue);
                    for (WarReward r : config.rewards) war.addReward(r);
                    plugin.getDatabaseManager().saveWar(war);
                }
                player.sendMessage("§a✔ Guerre déclarée contre §f" + config.targetTown + "§a !");
                playerConfigs.remove(player.getUniqueId());
            }
            case CIVIL_WAR_DISABLED -> player.sendMessage("§cLes guerres civiles sont désactivées !");
            case TOO_MANY_WARS -> player.sendMessage("§cVous avez atteint le nombre maximum de guerres simultanées.");
            case ON_COOLDOWN -> player.sendMessage("§cVous êtes encore en cooldown pour cette town.");
            case NOT_ENOUGH_MONEY -> player.sendMessage("§cFonds insuffisants dans la banque de votre town.");
            case ALREADY_AT_WAR -> player.sendMessage("§cVous êtes déjà en guerre avec cette town.");
        }
    }
}
