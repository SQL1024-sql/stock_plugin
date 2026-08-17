package io.github.sql1024.goldstock.listener;

import io.github.sql1024.goldstock.GoldStockPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Keeps the uuid to name mapping fresh so admin lookups and the leaderboard show real names. */
public final class PlayerListener implements Listener {

    private final GoldStockPlugin plugin;

    public PlayerListener(GoldStockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.rememberName(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
