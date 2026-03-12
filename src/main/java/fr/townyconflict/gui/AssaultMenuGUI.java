package fr.townyconflict.gui;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
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

public class AssaultMenuGUI implements Listener {

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
        if (res == null || !res.hasTown()) { player.sendMessage("§cVous n'avez pas de town."); return; }
        String myTownName = res.getTownOrNull().getName();

        List<War> wars = plugin.getWarManager().getActiveWarsForTown(myTownName);
        int slot = 10;
        for (War war : wars) {
            if (!war.isActive()) continue;
            if (slot >= 35) break;
            String opponent = war.getOpponent(myTownName);
            boolean isAttacker = war.isAttacker(myTownName);

            long cooldownMs = plugin.getConfigManager().getAssaultCooldownHours() * 3600_000L;
            long lastEnd = war.getLastAssaultEndTime();
            long remaining = lastEnd > 0 && !war.isCounterAttackAvailable()
                    ? Math.max(0, (lastEnd + cooldownMs) - System.currentTimeMillis()) : 0;
            boolean canAssault = isAttacker && remaining == 0;

            List<String> lore = new ArrayList<>();
            lore.add("&7Adversaire : &f" + opponent);
            lore.add("&7Votre rôle : " + (isAttacker ? "&cAttaquant" : "&aDefenseur"));
            if (remaining > 0) lore.add("&c⏳ Cooldown : " + GuiUtils.formatTime(remaining));
            else if (!isAttacker) lore.add("&7Défendez votre territoire !");
            else lore.add("&aVous pouvez lancer un assaut !");
            lore.add("");
            if (canAssault) lore.add("&eCliquez pour lancer l'assaut");

            inv.setItem(slot, canAssault
                    ? GuiUtils.glowing(plugin.getConfigManager().getGuiMaterial("assault_icon"),
                        "&c⚔ Attaquer &f" + opponent, lore)
                    : GuiUtils.item(plugin.getConfigManager().getGuiMaterial("assault_icon"),
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
                case SUCCESS -> {}
                case ON_COOLDOWN -> player.sendMessage("§cEncore en cooldown !");
                case NOT_ENOUGH_DEFENDERS -> player.sendMessage("§cPas assez de défenseurs connectés (" +
                        plugin.getConfigManager().getMinDefendersOnline() + " minimum).");
                case ALREADY_IN_ASSAULT -> player.sendMessage("§cUn assaut est déjà en cours contre cette town.");
                default -> player.sendMessage("§cImpossible de lancer l'assaut : " + result.name());
            }
        }
    }
}
