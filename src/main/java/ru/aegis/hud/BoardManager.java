package ru.aegis.hud;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class BoardManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Key FONT = Key.key("aegis", "icons");

    /** Иконки из ресурспака: в конфиге используются как <i_имя>. */
    private static final String[][] ICONS = {
            {"player", "\uE000"}, {"ping", "\uE001"}, {"amethyst", "\uE002"},
            {"calendar", "\uE003"}, {"clock", "\uE004"}, {"trophy", "\uE005"},
            {"skull", "\uE006"}, {"heart", "\uE007"}, {"sword", "\uE008"},
            {"stage", "\uE009"}, {"timer", "\uE00A"}, {"barrier", "\uE00B"}
    };

    private static final class Board {
        final Scoreboard sb;
        final Objective obj;
        final String mode;
        final List<Component> last;

        Board(Scoreboard sb, Objective obj, String mode, int n) {
            this.sb = sb;
            this.obj = obj;
            this.mode = mode;
            this.last = new ArrayList<>(Collections.nCopies(n, (Component) null));
        }
    }

    private final AegisHud plugin;
    private final Map<UUID, Board> boards = new HashMap<>();
    private String forcedMode = null;
    private int aliveCache = 0;
    private int titleTick = 0;

    public BoardManager(AegisHud plugin) {
        this.plugin = plugin;
    }

    public void setForcedMode(String m) {
        this.forcedMode = m;
    }

    public void reload() {
        forcedMode = null;
        removeAll();
    }

    /** Раз в секунду перед обновлением игроков. */
    public void prepare() {
        FileConfiguration cfg = plugin.getConfig();
        List<String> br = cfg.getStringList("br-worlds");
        int n = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if ((br.isEmpty() || br.contains(o.getWorld().getName())) && o.getGameMode() == GameMode.SURVIVAL) n++;
        }
        aliveCache = n;
    }

    private String modeOf(Player p) {
        String m = forcedMode != null ? forcedMode : plugin.getConfig().getString("mode", "auto");
        m = m.toLowerCase(Locale.ROOT);
        if (m.equals("hub") || m.equals("br")) return m;
        return plugin.getConfig().getStringList("br-worlds").contains(p.getWorld().getName()) ? "br" : "hub";
    }

    private Component title(double phase) {
        String t = plugin.getConfig().getString("title",
                "<gradient:#c084fc:#60a5fa:<phase>><bold>AEGIS</bold></gradient>");
        String ph = String.format(Locale.ROOT, "%.2f", phase);
        return MM.deserialize(t, Placeholder.parsed("phase", ph));
    }

    private Board create(String mode, int n) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("aegis", Criteria.DUMMY, title(0));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        try {
            obj.numberFormat(NumberFormat.blank()); // прячем красные цифры справа
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < n; i++) {
            Team t = sb.registerNewTeam("l" + i);
            String entry = "\u00a7" + Integer.toHexString(i) + "\u00a7r";
            t.addEntry(entry);
            obj.getScore(entry).setScore(n - i);
        }
        return new Board(sb, obj, mode, n);
    }

    public void update(Player p) {
        FileConfiguration cfg = plugin.getConfig();
        String mode = modeOf(p);
        List<String> raw = new ArrayList<>(cfg.getStringList(mode.equals("br") ? "br-lines" : "hub-lines"));
        if (raw.size() > 15) raw = new ArrayList<>(raw.subList(0, 15));
        TagResolver res = resolver(p);
        List<Component> comps = new ArrayList<>();
        for (String line : raw) comps.add(MM.deserialize(line, res));

        Board b = boards.get(p.getUniqueId());
        if (b == null || !b.mode.equals(mode) || b.last.size() != comps.size()) {
            b = create(mode, comps.size());
            boards.put(p.getUniqueId(), b);
        }
        if (p.getScoreboard() != b.sb) p.setScoreboard(b.sb);

        for (int i = 0; i < comps.size(); i++) {
            Component c = comps.get(i);
            if (!c.equals(b.last.get(i))) {
                Team t = b.sb.getTeam("l" + i);
                if (t != null) t.prefix(c);
                b.last.set(i, c);
            }
        }
    }

    /** Анимация заголовка (вызывается каждые 4 тика). */
    public void animate() {
        if (!plugin.getConfig().getBoolean("animate-title", true) || boards.isEmpty()) return;
        titleTick++;
        double phase = ((titleTick % 20) / 10.0) - 1.0;
        Component c = title(phase);
        for (Board b : boards.values()) {
            try {
                b.obj.displayName(c);
            } catch (Throwable ignored) {
            }
        }
    }

    public void remove(UUID id) {
        boards.remove(id);
    }

    public void removeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (boards.containsKey(p.getUniqueId())) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
    }

    private TagResolver resolver(Player p) {
        FileConfiguration cfg = plugin.getConfig();
        Stats s = plugin.data().get(p.getUniqueId(), p.getName());
        GameState g = plugin.game();
        GameState.Stage st = g.current();

        int barrier = g.barrierOverride != null ? g.barrierOverride : st.barrier;
        if (barrier < 0) barrier = (int) p.getWorld().getWorldBorder().getSize();
        int alive = g.aliveOverride != null ? g.aliveOverride : aliveCache;

        ZoneId zone;
        try {
            zone = ZoneId.of(cfg.getString("timezone", "Europe/Moscow"));
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        ZonedDateTime now = ZonedDateTime.now(zone);

        long h = s.seconds / 3600;
        long m = (s.seconds % 3600) / 60;
        String timer = g.remaining < 0 ? "--:--"
                : String.format(Locale.ROOT, "%d:%02d", g.remaining / 60, g.remaining % 60);

        List<TagResolver> list = new ArrayList<>();
        list.add(Placeholder.unparsed("nick", p.getName()));
        list.add(Placeholder.unparsed("ping", String.valueOf(p.getPing())));
        list.add(Placeholder.unparsed("amethysts", String.valueOf(s.amethysts)));
        list.add(Placeholder.unparsed("date", now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))));
        list.add(Placeholder.unparsed("time", now.format(DateTimeFormatter.ofPattern("HH:mm"))));
        list.add(Placeholder.unparsed("hours", h + "ч " + m + "мин"));
        list.add(Placeholder.unparsed("wins", String.valueOf(s.wins)));
        list.add(Placeholder.unparsed("losses", String.valueOf(s.losses)));
        list.add(Placeholder.unparsed("kills", String.valueOf(s.matchKills)));
        list.add(Placeholder.unparsed("totalkills", String.valueOf(s.kills)));
        list.add(Placeholder.unparsed("alive", String.valueOf(alive)));
        list.add(Placeholder.unparsed("stage", st.display));
        list.add(Placeholder.unparsed("timer", timer));
        list.add(Placeholder.unparsed("barrier", String.valueOf(barrier)));
        list.add(Placeholder.unparsed("ip", cfg.getString("ip", "aegis.gamepvp.ru")));
        for (String[] ic : ICONS) {
            list.add(Placeholder.component("i_" + ic[0], Component.text(ic[1]).font(FONT)));
        }
        return TagResolver.resolver(list);
    }
}
