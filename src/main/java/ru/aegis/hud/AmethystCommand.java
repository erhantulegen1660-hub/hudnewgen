package ru.aegis.hud;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;

/** /amethyst give|take|set <ник> <кол-во>, /amethyst balance [ник] */
public final class AmethystCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("give", "take", "set", "balance");
    private final AegisHud plugin;

    public AmethystCommand(AegisHud plugin) {
        this.plugin = plugin;
    }

    private static String esc(String s) {
        return MiniMessage.miniMessage().escapeTags(s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "balance" : args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("balance") || sub.equals("bal")) {
            Stats s;
            if (args.length >= 2) {
                if (!sender.hasPermission("aegis.admin")) {
                    plugin.msg(sender, "<red>Нет прав.");
                    return true;
                }
                s = plugin.data().findByName(args[1]);
                if (s == null) {
                    plugin.msg(sender, "<red>Игрок не найден.");
                    return true;
                }
            } else if (sender instanceof Player p) {
                s = plugin.data().get(p.getUniqueId(), p.getName());
            } else {
                plugin.msg(sender, "<gray>Использование: <white>/amethyst balance <ник>");
                return true;
            }
            plugin.msg(sender, "<light_purple>Аметисты <gray>" + esc(s.name) + ": <white><bold>" + s.amethysts);
            return true;
        }

        if (!sender.hasPermission("aegis.admin")) {
            plugin.msg(sender, "<red>Нет прав.");
            return true;
        }
        if (!SUBS.contains(sub) || args.length < 3) {
            plugin.msg(sender, "<gray>Использование: <white>/amethyst <give|take|set> <ник> <кол-во>");
            return true;
        }
        Stats s = plugin.data().findByName(args[1]);
        if (s == null) {
            plugin.msg(sender, "<red>Игрок не найден (он должен хотя бы раз зайти на сервер).");
            return true;
        }
        long n;
        try {
            n = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            plugin.msg(sender, "<red>Число указано неверно.");
            return true;
        }
        if (n < 0 || n > 1_000_000_000L) {
            plugin.msg(sender, "<red>Число должно быть от 0 до 1 000 000 000.");
            return true;
        }

        switch (sub) {
            case "give" -> s.amethysts = Math.min(2_000_000_000L, s.amethysts + n);
            case "take" -> s.amethysts = Math.max(0, s.amethysts - n);
            default -> s.amethysts = n;
        }
        plugin.data().save();
        plugin.msg(sender, "<green>Баланс " + esc(s.name) + ": <light_purple><bold>" + s.amethysts + " <gray>аметистов");

        Player online = Bukkit.getPlayerExact(s.name);
        if (online != null && sub.equals("give")) {
            plugin.msg(online, "<green>Вам выдано <light_purple><bold>" + n + " <gray>аметистов!");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && sender.hasPermission("aegis.admin")) {
            for (String n : plugin.data().names()) if (n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(n);
        }
        return out;
    }
}
