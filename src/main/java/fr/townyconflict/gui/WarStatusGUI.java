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

public class WarStatusGUI implements Listener {

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
            lore.add("&7Points guerre : &f" + war.getWarPoints(town.getName()) + " &8vs &f" + war.getWarPoints(opponent));
            lore.add("&7Assauts gagnés : &f" + war.getAttackerAssaultWins() + " &8vs &f" + war.getDefenderAssaultWins());
            lore.add("");
            lore.add("&7Renforts : " + (war.isAllowNationReinforcements() ? "&aOui" : "&cNon"));
            lore.add("&7Mercenaires : " + (war.isAllowMercenaries() ? "&aOui" : "&cNon"));
            lore.add("");
            lore.add("&7Récompenses :");
            war.getRewards().forEach(r -> lore.add("  &8- &f" + r.describe()));
            lore.add("");
            if (war.getDaysElapsed() >= plugin.getConfigManager().getSurrenderMinDays()) {
                lore.add("&cClic droit pour capituler");
            } else {
                lore.add("&7Capitulation dans &f" +
                        (plugin.getConfigManager().getSurrenderMinDays() - war.getDaysElapsed()) + " jours");
            }

            inv.setItem(slot, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("war_icon"),
                    "&cvs &f" + opponent, lore));
            slot += 2;
        }

        if (wars.isEmpty()) {
            inv.setItem(22, GuiUtils.item(org.bukkit.Material.WHITE_BANNER,
                    "&7Aucune guerre active", "&7Votre town est en paix."));
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
