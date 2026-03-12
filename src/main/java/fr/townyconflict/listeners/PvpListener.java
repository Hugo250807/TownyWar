package fr.townyconflict.listeners;

import fr.townyconflict.TownyConflict;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PvpListener implements Listener {

    private final TownyConflict plugin;

    public PvpListener(TownyConflict plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        plugin.getAssaultManager().onPlayerKill(killer, victim);
    }
}
