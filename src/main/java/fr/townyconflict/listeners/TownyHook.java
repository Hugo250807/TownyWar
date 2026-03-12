package fr.townyconflict.listeners;

import com.palmergames.bukkit.towny.event.TownRemoveResidentEvent;
import fr.townyconflict.TownyConflict;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class TownyHook implements Listener {

    private final TownyConflict plugin;

    public TownyHook(TownyConflict plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onResidentLeave(TownRemoveResidentEvent event) {
        // Si un joueur quitte une town pendant un assaut, le retirer des participants
        String townName = event.getTown().getName();
        var assault = plugin.getAssaultManager().getAssaultForTown(townName);
        if (assault != null) {
            assault.removePlayer(event.getResident().getUUID());
        }
    }
}
