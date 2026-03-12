package fr.townyconflict.listeners;

import fr.townyconflict.TownyConflict;
import fr.townyconflict.models.Assault;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class AssaultListener implements Listener {

    private final TownyConflict plugin;

    public AssaultListener(TownyConflict plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        for (Assault assault : plugin.getAssaultManager().getAllActiveAssaults()) {
            if (assault.isParticipant(event.getPlayer().getUniqueId())) {
                // Si mercenaire, traiter comme désertion
                if (assault.getMercenaries().contains(event.getPlayer().getUniqueId())) {
                    plugin.getMercenaryManager().handleDesertion(event.getPlayer().getUniqueId(), assault);
                } else {
                    assault.removePlayer(event.getPlayer().getUniqueId());
                }
                break;
            }
        }
    }
}
