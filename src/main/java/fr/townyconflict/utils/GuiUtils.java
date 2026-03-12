package fr.townyconflict.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GuiUtils {

    public static ItemStack item(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(colorize(name)));
        if (lore.length > 0) {
            meta.lore(Arrays.stream(lore).map(l -> Component.text(colorize(l))).collect(Collectors.toList()));
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack item(Material mat, String name, List<String> lore) {
        return item(mat, name, lore.toArray(new String[0]));
    }

    public static ItemStack glowing(Material mat, String name, String... lore) {
        ItemStack item = item(mat, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.LUCK, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static String colorize(String s) {
        return s.replace("&", "§");
    }

    public static String formatTime(long ms) {
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + (seconds % 60) + "s";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h " + (minutes % 60) + "m";
        long days = hours / 24;
        return days + "j " + (hours % 24) + "h";
    }
}
