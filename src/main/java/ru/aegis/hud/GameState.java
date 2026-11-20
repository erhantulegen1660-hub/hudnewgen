package ru.aegis.hud;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/** Текущая стадия матча Королевской битвы. */
public final class GameState {
    public static final class Stage {
        public final String id;
        public final String display;
        public final int duration;
        public final int barrier;

        Stage(String id, String display, int duration, int barrier) {
            this.id = id;
            this.display = display;
            this.duration = duration;
            this.barrier = barrier;
        }
    }

    private final AegisHud plugin;
    private final List<Stage> stages = new ArrayList<>();
    private int index = 0;
    /** Секунд до смены стадии. -1 = без таймера. */
    public int remaining = -1;
    public Integer aliveOverride = null;
    public Integer barrierOverride = null;

    public GameState(AegisHud plugin) {
        this.plugin = plugin;
    }

    public void load() {
        stages.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("stages");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(id);
                if (s == null) continue;
                stages.add(new Stage(id.toLowerCase(), s.getString("name", id),
                        s.getInt("duration", -1), s.getInt("barrier", -1)));
            }
        }
        if (stages.isEmpty()) stages.add(new Stage("start", "Начало", -1, -1));
        index = 0;
        remaining = stages.get(0).duration > 0 ? stages.get(0).duration : -1;
    }

    public Stage current() {
        return stages.get(Math.max(0, Math.min(index, stages.size() - 1)));
    }

    public boolean set(String id) {
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).id.equalsIgnoreCase(id)) {
                setIndex(i);
                return true;
            }
        }
        return false;
    }

    public void next() {
        if (index < stages.size() - 1) setIndex(index + 1);
    }

    private void setIndex(int i) {
        index = i;
        remaining = stages.get(i).duration > 0 ? stages.get(i).duration : -1;
    }

    public List<String> ids() {
        List<String> out = new ArrayList<>();
        for (Stage s : stages) out.add(s.id);
        return out;
    }

    /** Вызывается раз в секунду. */
    public void tick() {
        if (remaining <= 0) return;
        remaining--;
        if (remaining == 0 && plugin.getConfig().getBoolean("auto-advance", false)) {
            next();
        }
    }
}
