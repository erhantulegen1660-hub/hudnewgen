package ru.aegis.hud;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public final class DataStore {
    private final AegisHud plugin;
    private final File file;
    private final Map<UUID, Stats> map = new LinkedHashMap<>();

    public DataStore(AegisHud plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        map.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection c = sec.getConfigurationSection(key);
                if (c == null) continue;
                Stats s = new Stats();
                s.name = c.getString("name", "");
                s.seconds = c.getLong("seconds");
                s.kills = c.getInt("kills");
                s.wins = c.getInt("wins");
                s.losses = c.getInt("losses");
                s.amethysts = c.getLong("amethysts");
                map.put(id, s);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Stats> e : map.entrySet()) {
            String b = "players." + e.getKey() + ".";
            Stats s = e.getValue();
            y.set(b + "name", s.name);
            y.set(b + "seconds", s.seconds);
            y.set(b + "kills", s.kills);
            y.set(b + "wins", s.wins);
            y.set(b + "losses", s.losses);
            y.set(b + "amethysts", s.amethysts);
        }
        try {
            plugin.getDataFolder().mkdirs();
            File tmp = new File(plugin.getDataFolder(), "data.yml.tmp");
            y.save(tmp);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить data.yml: " + ex.getMessage());
        }
    }

    public Stats get(UUID id, String name) {
        Stats s = map.computeIfAbsent(id, k -> new Stats());
        if (name != null && !name.equals(s.name)) s.name = name;
        return s;
    }

    public Stats findByName(String name) {
        for (Stats s : map.values()) {
            if (s.name.equalsIgnoreCase(name)) return s;
        }
        return null;
    }

    public Collection<Stats> all() {
        return map.values();
    }

    public List<String> names() {
        List<String> out = new ArrayList<>();
        for (Stats s : map.values()) if (!s.name.isEmpty()) out.add(s.name);
        return out;
    }
}
