package fr.townyconflict.gui;

import fr.townyconflict.TownyConflict;
import fr.townyconflict.utils.GuiUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class MercMenuGUI implements Listener {

    private final TownyConflict plugin;

    public MercMenuGUI(TownyConflict plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36,
                net.kyori.adventure.text.Component.text(plugin.getConfigManager().getGuiTitle("merc_menu")));
        for (int i = 0; i < 36; i++) inv.setItem(i, GuiUtils.filler());

        var offer = plugin.getMercenaryManager().getPendingOffer(player.getUniqueId());
        if (offer != null) {
            inv.setItem(13, GuiUtils.glowing(plugin.getConfigManager().getGuiMaterial("merc_icon"),
                    "&6⚔ Offre de " + offer.hiringTown,
                    "&7Montant total : &f" + String.format("%.0f", offer.totalAmount) + "$",
                    "&7Camp : " + (offer.forAttacker ? "&cAttaquants" : "&aDefenseurs"),
                    "", "&aCliquez pour ACCEPTER"));
            inv.setItem(15, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("cancel"),
                    "&cRefuser l'offre", "&7Refuser l'offre de &f" + offer.hiringTown));
        } else if (plugin.getMercenaryManager().isOnDesertionCooldown(player.getUniqueId())) {
            long remaining = plugin.getMercenaryManager().getDesertionCooldownRemaining(player.getUniqueId());
            inv.setItem(13, GuiUtils.item(org.bukkit.Material.BARRIER, "&cCooldown de désertion",
                    "&7Indisponible encore &f" + GuiUtils.formatTime(remaining)));
        } else {
            inv.setItem(13, GuiUtils.item(plugin.getConfigManager().getGuiMaterial("merc_icon"),
                    "&7Aucune offre en attente",
                    "&7Attendez qu'une town vous recrute via",
                    "&f/tc merc hire <joueur> <montant>"));
        }

        inv.setItem(4, GuiUtils.item(org.bukkit.Material.PAPER, "&e📋 Système Mercenaire",
                "&7Max par assaut : &f" + plugin.getConfigManager().getMercMaxPerTeam(),
                "&7Acompte : &f" + plugin.getConfigManager().getMercUpfrontPaymentPercent() + "% à la signature",
                "&7Reste : versé à la victoire",
                "&7Désertion : cooldown &f" + plugin.getConfigManager().getMercDesertionCooldownDays() + " jours"));

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
