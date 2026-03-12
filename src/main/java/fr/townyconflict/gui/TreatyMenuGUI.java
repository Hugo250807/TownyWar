package fr.townyconflict.gui;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import fr.townyconflict.TownyConflict;
import fr.townyconflict.managers.TreatyManager;
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

public class TreatyMenuGUI implements Listener {

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
                if (slot >= 26) break;
                String opponent = war.getOpponent(town.getName());
                long minDays = plugin.getConfigManager().getTreatyAvailableAfterDays();
                boolean canPropose = war.getDaysElapsed() >= minDays;

                List<String> lore = new ArrayList<>();
                lore.add("&7Guerre vs &f" + opponent);
                lore.add("&7Jours écoulés : &f" + war.getDaysElapsed());
                if (!canPropose) lore.add("&c⏳ Disponible après &f" + minDays + " jours");
                else lore.add("&eCliquez pour proposer un traité");

                inv.setItem(slot, canPropose
                        ? GuiUtils.glowing(plugin.getConfigManager().getGuiMaterial("treaty_icon"),
                            "&b🕊 " + opponent, lore)
                        : GuiUtils.item(plugin.getConfigManager().getGuiMaterial("treaty_icon"),
                            "&7vs " + opponent, lore));
                slot += 2;
            }
        }

        // Proposition reçue en attente ?
        for (War war : wars) {
            var proposal = plugin.getTreatyManager().getPendingProposal(war.getId());
            if (proposal != null && proposal.targetTown.equalsIgnoreCase(town.getName())) {
                inv.setItem(29, GuiUtils.glowing(org.bukkit.Material.WHITE_BANNER,
                        "&a⚡ Traité de " + proposal.proposerTown,
                        "&7Type : &f" + proposal.type.name(),
                        "", "&aCliquez pour ACCEPTER"));
                inv.setItem(27, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("cancel"),
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

        if (name.contains("Traité de") || name.contains("⚡ Traité")) {
            plugin.getTreatyManager().accept(myTown);
            player.closeInventory();
            player.sendMessage("§aTraité accepté !");
            return;
        }
        if (name.contains("Refuser")) {
            plugin.getTreatyManager().refuse(myTown);
            player.closeInventory();
            player.sendMessage("§7Traité refusé.");
            return;
        }

        // Proposer un traité
        if (name.startsWith("🕊") || name.startsWith("vs ")) {
            String opponent = name.replace("🕊 ", "").replace("vs ", "").trim();
            openTreatyTypeSelection(player, myTown, opponent);
        }
    }

    private void openTreatyTypeSelection(Player player, String myTown, String opponent) {
        Inventory inv = Bukkit.createInventory(null, 27,
                net.kyori.adventure.text.Component.text("§8Proposer un traité à " + opponent));
        for (int i = 0; i < 27; i++) inv.setItem(i, GuiUtils.filler());

        if (plugin.getConfigManager().isTreatyTypeEnabled("peace")) {
            inv.setItem(11, GuiUtils.item(org.bukkit.Material.WHITE_BANNER, "&bTraité de paix",
                    "&7Mettre fin à la guerre.", "&eCliquez pour proposer"));
        }
        if (plugin.getConfigManager().isTreatyTypeEnabled("non_aggression")) {
            inv.setItem(13, GuiUtils.item(org.bukkit.Material.WHITE_WOOL, "&bNon-agression",
                    "&7Aucun assaut pendant une durée.", "&eCliquez pour proposer"));
        }
        if (plugin.getConfigManager().isTreatyTypeEnabled("ceasefire")) {
            inv.setItem(15, GuiUtils.item(org.bukkit.Material.CLOCK, "&bCessez-le-feu",
                    "&7Pause temporaire.", "&eCliquez pour proposer"));
        }
        inv.setItem(22, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("back"), "&7← Retour"));

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

                TreatyManager.TreatyType type = null;
                if (n.contains("paix")) type = TreatyManager.TreatyType.PEACE;
                else if (n.contains("agression")) type = TreatyManager.TreatyType.NON_AGGRESSION;
                else if (n.contains("feu")) type = TreatyManager.TreatyType.CEASEFIRE;

                if (type != null) {
                    var result = plugin.getTreatyManager().propose(myTown, opponent, type, 7);
                    player.closeInventory();
                    player.sendMessage(result == TreatyManager.ProposeResult.SUCCESS
                            ? "§bProposition envoyée à §f" + opponent + "§b !"
                            : "§cImpossible : " + result.name());
                }
            }
        }, plugin);

        player.openInventory(inv);
    }
}
