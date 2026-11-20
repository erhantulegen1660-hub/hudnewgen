package ru.aegis.hud;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AegisHud extends JavaPlugin {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private DataStore data;
    private GameState game;
    private BoardManager boards;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = new DataStore(this);
        data.load();
        game = new GameState(this);
        game.load();
        boards = new BoardManager(this);

        getServer().getPluginManager().registerEvents(new HudListener(this), this);
        bind("aegishud", new HudCommand(this));
        bind("amethyst", new AmethystCommand(this));

        getServer().getScheduler().runTaskTimer(this, this::secondTick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> boards.animate(), 4L, 4L);
        getServer().getScheduler().runTaskTimer(this, () -> data.save(), 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        if (boards != null) boards.removeAll();
        if (data != null) data.save();
    }

    private void bind(String name, TabExecutor ex) {
        PluginCommand c = getCommand(name);
        if (c == null) {
            getLogger().warning("Команда " + name + " не найдена в plugin.yml");
            return;
        }
        c.setExecutor(ex);
        c.setTabCompleter(ex);
    }

    private void secondTick() {
        try {
            game.tick();
            boards.prepare();
            for (Player p : getServer().getOnlinePlayers()) {
                try {
                    data.get(p.getUniqueId(), p.getName()).seconds++;
                    boards.update(p);
                } catch (Throwable t) {
                    getLogger().warning("Ошибка HUD у " + p.getName() + ": " + t);
                }
            }
        } catch (Throwable t) {
            getLogger().warning("Ошибка HUD: " + t);
        }
    }

    public void msg(CommandSender s, String mini) {
        String prefix = getConfig().getString("prefix", "<gradient:#c084fc:#60a5fa><bold>AEGIS</bold></gradient> <dark_gray>» <gray>");
        s.sendMessage(MM.deserialize(prefix + mini));
    }

    public DataStore data() { return data; }
    public GameState game() { return game; }
    public BoardManager boards() { return boards; }
}
