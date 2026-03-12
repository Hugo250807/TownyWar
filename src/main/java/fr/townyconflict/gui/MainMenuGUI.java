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
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class MainMenuGUI implements Listener {

    private final TownyConflict plugin;

    public MainMenuGUI(TownyConflict plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text(plugin.getConfigManager().getGuiTitle("main_menu")));

        // Remplissage
        for (int i = 0; i < 54; i++) inv.setItem(i, GuiUtils.filler());

        Resident res = TownyAPI.getInstance().getResident(player.getName());
        Town town = res != null && res.hasTown() ? res.getTownOrNull() : null;

        // ── Déclarer une guerre ──
        List<String> warLore = new ArrayList<>();
        if (town != null) {
            warLore.add("&7Town : &f" + town.getName());
            warLore.add("&7Guerres actives : &f" + plugin.getWarManager().getActiveWarsForTown(town.getName()).size() +
                    "/" + plugin.getConfigManager().getMaxSimultaneousWars());
            warLore.add("");
            warLore.add("&eCliquez pour déclarer une guerre");
        } else {
            warLore.add("&cVous n'avez pas de town.");
        }
        inv.setItem(11, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("war_icon"),
                "&c⚔ Déclarer une guerre", warLore));

        // ── Statut des guerres ──
        List<String> statusLore = new ArrayList<>();
        if (town != null) {
            List<War> wars = plugin.getWarManager().getActiveWarsForTown(town.getName());
            if (wars.isEmpty()) {
                statusLore.add("&7Aucune guerre active.");
            } else {
                for (War w : wars) {
                    statusLore.add("&cvs &f" + w.getOpponent(town.getName()) +
                            " &7(" + w.getStatus().name() + ")");
                }
            }
        }
        statusLore.add("");
        statusLore.add("&eCliquez pour voir le détail");
        inv.setItem(13, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("info"),
                "&6📋 Mes guerres", statusLore));

        // ── Assaut ──
        List<String> assaultLore = new ArrayList<>();
        if (town != null) {
            boolean inWar = !plugin.getWarManager().getActiveWarsForTown(town.getName()).isEmpty();
            assaultLore.add(inWar ? "&aVous pouvez lancer un assaut" : "&cAucune guerre active");
            assaultLore.add("");
            assaultLore.add("&eCliquez pour ouvrir le menu d'assaut");
        }
        inv.setItem(15, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("assault_icon"),
                "&c🗡 Assaut", assaultLore));

        // ── Mercenaires ──
        List<String> mercLore = new ArrayList<>();
        mercLore.add("&7Recrutez des joueurs neutres");
        mercLore.add("&7pour vos assauts.");
        mercLore.add("");
        if (plugin.getMercenaryManager().isOnDesertionCooldown(player.getUniqueId())) {
            mercLore.add("&c⏳ Cooldown de désertion actif !");
        } else {
            mercLore.add("&eCliquez pour ouvrir le menu mercenaire");
        }
        inv.setItem(29, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("merc_icon"),
                "&6🗡 Mercenaires", mercLore));

        // ── Traités ──
        List<String> treatyLore = new ArrayList<>();
        treatyLore.add("&7Proposez des accords de paix,");
        treatyLore.add("&7non-agression ou cessez-le-feu.");
        treatyLore.add("");
        treatyLore.add("&eCliquez pour proposer un traité");
        inv.setItem(31, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("treaty_icon"),
                "&b🕊 Traités", treatyLore));

        // ── Réputation ──
        int rep = town != null ? plugin.getReputationManager().getReputation(town.getName()) : 0;
        String repLabel = plugin.getReputationManager().getReputationLabel(rep);
        inv.setItem(33, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("info"),
                "&e⭐ Réputation",
                "&7Réputation de " + (town != null ? town.getName() : "N/A") + " :",
                repLabel + " &f(" + rep + ")",
                "",
                "&7Modifie le coût de déclaration",
                "&7et la disponibilité des mercenaires."));

        // ── Leaderboard ──
        inv.setItem(49, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("info"),
                "&e🏆 Classement", "&eCliquez pour voir le classement de réputation"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());
        if (!title.contains("TownyConflict")) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        ItemStack item = event.getCurrentItem();
        String itemName = item.getItemMeta() != null ?
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(item.getItemMeta().displayName()) : "";

        if (itemName.contains("Déclarer une guerre")) {
            new WarDeclareGUI(plugin).open(player);
        } else if (itemName.contains("Mes guerres")) {
            new WarStatusGUI(plugin).open(player);
        } else if (itemName.contains("Assaut")) {
            new AssaultMenuGUI(plugin).open(player);
        } else if (itemName.contains("Mercenaires")) {
            new MercMenuGUI(plugin).open(player);
        } else if (itemName.contains("Traités")) {
            new TreatyMenuGUI(plugin).open(player);
        } else if (itemName.contains("Classement")) {
            openLeaderboard(player);
        }
    }

    private void openLeaderboard(Player player) {
        player.closeInventory();
        player.sendMessage("§e§l─── Classement Réputation ───");
        // Affichage dans le chat (peut être étendu en GUI)
        plugin.getReputationManager();
        player.sendMessage("§7(Classement en développement)");
    }
}
