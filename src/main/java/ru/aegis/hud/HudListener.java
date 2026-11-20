package ru.aegis.hud;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public final class HudListener implements Listener {
    private final AegisHud plugin;

    public HudListener(AegisHud plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.data().get(p.getUniqueId(), p.getName());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) plugin.boards().update(p);
        }, 5L);

        FileConfiguration cfg = plugin.getConfig();
        String url = cfg.getString("resource-pack.url", "");
        if (url != null && !url.isBlank()) {
            byte[] hash = hex(cfg.getString("resource-pack.sha1", ""));
            boolean force = cfg.getBoolean("resource-pack.force", false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    if (p.isOnline()) p.setResourcePack(url, hash, force);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Не удалось отправить ресурспак: " + t.getMessage());
                }
            }, 20L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.boards().remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        FileConfiguration cfg = plugin.getConfig();
        Player victim = e.getEntity();
        List<String> br = cfg.getStringList("br-worlds");
        if (!br.contains(victim.getWorld().getName())) return;

        if (cfg.getBoolean("tracking.kills", true)) {
            Player killer = victim.getKiller();
            if (killer != null && !killer.equals(victim)) {
                Stats ks = plugin.data().get(killer.getUniqueId(), killer.getName());
                ks.kills++;
                ks.matchKills++;
            }
        }
        if (cfg.getBoolean("tracking.deaths-as-losses", false)) {
            plugin.data().get(victim.getUniqueId(), victim.getName()).losses++;
        }
    }

    private static byte[] hex(String s) {
        try {
            if (s == null || s.length() != 40) return null;
            byte[] b = new byte[20];
            for (int i = 0; i < 20; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
            return b;
        } catch (Exception ex) {
            return null;
        }
    }
}
