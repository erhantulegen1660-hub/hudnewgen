package ru.aegis.hud;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.*;

/** /aegishud (алиас /hud) — управление HUD, стадиями и статистикой. */
public final class HudCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("stage", "next", "timer", "barrier", "alive", "mode",
            "resetmatch", "add", "set", "addall", "stats", "reload");
    private static final List<String> FIELDS = List.of("hours", "kills", "wins", "losses", "amethysts");
    private final AegisHud plugin;

    public HudCommand(AegisHud plugin) {
        this.plugin = plugin;
    }

    private static String esc(String s) {
        return MiniMessage.miniMessage().escapeTags(s);
    }

    private static Double num(String s) {
        try {
            double d = Double.parseDouble(s);
            if (Double.isNaN(d) || Double.isInfinite(d) || Math.abs(d) > 1_000_000_000d) return null;
            return d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean change(Stats s, String f, double n, boolean set) {
        switch (f) {
            case "hours" -> {
                long v = (long) (n * 3600);
                s.seconds = set ? Math.max(0, v) : Math.max(0, s.seconds + v);
            }
            case "kills" -> s.kills = (int) Math.max(0, set ? n : s.kills + n);
            case "wins" -> s.wins = (int) Math.max(0, set ? n : s.wins + n);
            case "losses" -> s.losses = (int) Math.max(0, set ? n : s.losses + n);
            case "amethysts" -> s.amethysts = (long) Math.max(0, set ? n : s.amethysts + n);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void help(CommandSender s) {
        plugin.msg(s, "<white>Команды:");
        String[] lines = {
                "/hud stage <id> — сменить стадию; /hud next — следующая",
                "/hud timer <сек> — таймер до смены (-1 = выкл)",
                "/hud barrier <число|auto> — размер барьера в HUD",
                "/hud alive <число|auto> — число живых в HUD",
                "/hud mode <auto|hub|br> — принудительный вид HUD",
                "/hud resetmatch — обнулить убийства матча",
                "/hud add|set <ник> <hours|kills|wins|losses|amethysts> <число>",
                "/hud addall <hours|kills|wins|losses|amethysts> <число> — накрутка ВСЕМ",
                "/hud stats <ник>; /hud reload",
                "/amethyst give|take|set <ник> <число>"
        };
        for (String l : lines) s.sendMessage(MiniMessage.miniMessage().deserialize("<gray> " + esc(l)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("aegis.admin")) {
            plugin.msg(sender, "<red>Нет прав.");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        GameState g = plugin.game();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadConfig();
                g.load();
                plugin.boards().reload();
                plugin.msg(sender, "<green>Конфиг перезагружен.");
            }
            case "stage" -> {
                if (args.length < 2) {
                    plugin.msg(sender, "<gray>Стадии: <white>" + esc(String.join(", ", g.ids())));
                } else if (g.set(args[1])) {
                    plugin.msg(sender, "<green>Стадия: <white>" + esc(g.current().display));
                } else {
                    plugin.msg(sender, "<red>Нет такой стадии. <gray>Доступны: <white>" + esc(String.join(", ", g.ids())));
                }
            }
            case "next" -> {
                g.next();
                plugin.msg(sender, "<green>Стадия: <white>" + esc(g.current().display));
            }
            case "timer" -> {
                Double n = args.length > 1 ? num(args[1]) : null;
                if (n == null) {
                    plugin.msg(sender, "<gray>Использование: <white>/hud timer <секунды>");
                } else {
                    g.remaining = n.intValue();
                    plugin.msg(sender, "<green>Таймер: <white>" + g.remaining);
                }
            }
            case "barrier" -> {
                if (args.length < 2) {
                    plugin.msg(sender, "<gray>Использование: <white>/hud barrier <число|auto>");
                } else if (args[1].equalsIgnoreCase("auto")) {
                    g.barrierOverride = null;
                    plugin.msg(sender, "<green>Барьер: из стадии.");
                } else {
                    Double n = num(args[1]);
                    if (n == null) {
                        plugin.msg(sender, "<red>Число указано неверно.");
                    } else {
                        g.barrierOverride = n.intValue();
                        plugin.msg(sender, "<green>Барьер: <white>" + n.intValue());
                    }
                }
            }
            case "alive" -> {
                if (args.length < 2) {
                    plugin.msg(sender, "<gray>Использование: <white>/hud alive <число|auto>");
                } else if (args[1].equalsIgnoreCase("auto")) {
                    g.aliveOverride = null;
                    plugin.msg(sender, "<green>Живые считаются автоматически.");
                } else {
                    Double n = num(args[1]);
                    if (n == null) {
                        plugin.msg(sender, "<red>Число указано неверно.");
                    } else {
                        g.aliveOverride = Math.max(0, n.intValue());
                        plugin.msg(sender, "<green>Живых: <white>" + g.aliveOverride);
                    }
                }
            }
            case "mode" -> {
                String m = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                if (m.equals("auto") || m.equals("hub") || m.equals("br")) {
                    plugin.boards().setForcedMode(m);
                    plugin.msg(sender, "<green>Режим HUD: <white>" + m);
                } else {
                    plugin.msg(sender, "<gray>Использование: <white>/hud mode <auto|hub|br>");
                }
            }
            case "resetmatch" -> {
                for (Stats s : plugin.data().all()) s.matchKills = 0;
                plugin.msg(sender, "<green>Убийства матча обнулены.");
            }
            case "add", "set" -> {
                boolean set = args[0].equalsIgnoreCase("set");
                if (args.length < 4) {
                    plugin.msg(sender, "<gray>Использование: <white>/hud " + args[0] + " <ник> <hours|kills|wins|losses|amethysts> <число>");
                    return true;
                }
                Stats s = plugin.data().findByName(args[1]);
                Double n = num(args[3]);
                if (s == null) {
                    plugin.msg(sender, "<red>Игрок не найден (он должен хотя бы раз зайти).");
                } else if (n == null) {
                    plugin.msg(sender, "<red>Число указано неверно.");
                } else if (!change(s, args[2].toLowerCase(Locale.ROOT), n, set)) {
                    plugin.msg(sender, "<red>Поле: hours, kills, wins, losses или amethysts.");
                } else {
                    plugin.data().save();
                    plugin.msg(sender, "<green>Готово: <white>" + esc(s.name) + " <gray>" + esc(args[2]) + (set ? " = " : " += ") + n);
                }
            }
            case "addall" -> {
                if (args.length < 3) {
                    plugin.msg(sender, "<gray>Использование: <white>/hud addall <hours|kills|wins|losses|amethysts> <число>");
                    return true;
                }
                Double n = num(args[2]);
                String f = args[1].toLowerCase(Locale.ROOT);
                if (n == null) {
                    plugin.msg(sender, "<red>Число указано неверно.");
                } else if (!FIELDS.contains(f)) {
                    plugin.msg(sender, "<red>Поле: hours, kills, wins, losses или amethysts.");
                } else {
                    int count = 0;
                    for (Stats s : plugin.data().all()) {
                        change(s, f, n, false);
                        count++;
                    }
                    plugin.data().save();
                    plugin.msg(sender, "<green>Накручено всем (" + count + " игроков): <white>" + f + " += " + n);
                }
            }
            case "stats" -> {
                Stats s = args.length > 1 ? plugin.data().findByName(args[1]) : null;
                if (s == null) {
                    plugin.msg(sender, "<red>Игрок не найден. <gray>/hud stats <ник>");
                } else {
                    plugin.msg(sender, "<white>" + esc(s.name) + "<gray>: часы <white>" + (s.seconds / 3600) + "ч " + ((s.seconds % 3600) / 60)
                            + "мин<gray>, победы <white>" + s.wins + "<gray>, поражения <white>" + s.losses
                            + "<gray>, убийства <white>" + s.kills + "<gray>, аметисты <white>" + s.amethysts);
                }
            }
            default -> help(sender);
        }
        return true;
    }

    private static void filter(List<String> src, String prefix, List<String> out) {
        for (String s : src) if (s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) out.add(s);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("aegis.admin")) return out;
        if (args.length == 1) {
            filter(SUBS, args[0], out);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "stage" -> filter(plugin.game().ids(), args[1], out);
                case "mode" -> filter(List.of("auto", "hub", "br"), args[1], out);
                case "barrier", "alive" -> filter(List.of("auto"), args[1], out);
                case "add", "set", "stats" -> filter(plugin.data().names(), args[1], out);
                case "addall" -> filter(FIELDS, args[1], out);
                default -> {
                }
            }
        } else if (args.length == 3) {
            String a = args[0].toLowerCase(Locale.ROOT);
            if (a.equals("add") || a.equals("set")) filter(FIELDS, args[2], out);
        }
        return out;
    }
}
