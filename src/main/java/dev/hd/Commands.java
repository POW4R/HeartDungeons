package dev.hd;

import dev.hd.dungeon.DungeonRun;
import dev.hd.dungeon.Elites;
import dev.hd.progress.ProgressManager;
import dev.hd.util.Text;
import dev.hd.weapon.Weapons;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /dungeon, /stats and /withdraw. */
public final class Commands implements TabExecutor {

    private final HeartDungeons plugin;

    public Commands(HeartDungeons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "stats" -> stats(sender, args);
            case "withdraw" -> withdraw(sender, args);
            default -> dungeon(sender, args);
        }
        return true;
    }

    // ------------------------------------------------------------ /stats

    private void stats(CommandSender sender, String[] args) {
        Player target = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : sender instanceof Player p ? p : null;
        if (target == null) {
            sender.sendMessage(Text.error("Player not found."));
            return;
        }
        ProgressManager pm = plugin.progress();
        int level = pm.level(target);
        int per = plugin.getConfig().getInt("hearts.per-levels", 5);
        int nextHeart = ((level / per) + 1) * per;
        sender.sendMessage(Text.of(target.getName(), Text.GOLD));
        sender.sendMessage(Component.text("Level ", Text.MUTED).append(Component.text(level, Text.GOLD))
                .append(Component.text("   XP " + pm.xp(target) + "/" + pm.xpForNext(level), Text.MUTED)));
        sender.sendMessage(Component.text("Hearts ", Text.MUTED).append(Component.text("❤ " + pm.hearts(target), Text.HEART))
                .append(Component.text(" / " + pm.maxHearts() + "   next heart at level " + nextHeart, Text.MUTED)));
    }

    // ------------------------------------------------------------ /withdraw

    private void withdraw(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Run this in-game.");
            return;
        }
        int amount = 1;
        if (args.length > 0) {
            try {
                amount = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                p.sendMessage(Text.error("Usage: /withdraw [amount]"));
                return;
            }
        }
        String error = plugin.progress().withdraw(p, amount);
        if (error != null) p.sendMessage(Text.error(error));
        else p.sendMessage(Text.info("Withdrew " + amount + " heart" + (amount > 1 ? "s" : "") + ". You now have "
                + plugin.progress().hearts(p) + "."));
    }

    // ------------------------------------------------------------ /dungeon

    private void dungeon(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            admin(sender, args);
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Run this in-game.");
            return;
        }
        switch (sub) {
            case "start" -> {
                int tier = 1;
                if (args.length > 1) {
                    try {
                        tier = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                String error = plugin.dungeons().start(p, tier);
                if (error != null) p.sendMessage(Text.error(error));
            }
            case "leave" -> plugin.dungeons().leave(p);
            case "tiers" -> {
                int level = plugin.progress().level(p);
                for (int t = 1; t <= 5; t++) {
                    int need = plugin.dungeons().tierLevel(t);
                    boolean open = level >= need;
                    p.sendMessage(Text.of("Tier " + t + "  " + (open ? "✔ open" : "✖ needs level " + need),
                            open ? Elites.tierColor(t) : Text.MUTED));
                }
            }
            case "party" -> party(p, args);
            default -> help(p);
        }
    }

    private void party(Player p, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "invite" -> {
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null) {
                    p.sendMessage(Text.error("Usage: /dungeon party invite <player>"));
                    return;
                }
                String error = plugin.dungeons().invite(p, target);
                p.sendMessage(error != null ? Text.error(error) : Text.info("Invited " + target.getName() + "."));
            }
            case "accept" -> {
                String error = plugin.dungeons().accept(p);
                if (error != null) p.sendMessage(Text.error(error));
            }
            case "leave" -> plugin.dungeons().leaveParty(p);
            default -> {
                List<String> names = plugin.dungeons().partyNames(p);
                p.sendMessage(names.isEmpty() ? Text.info("You're not in a party. /dungeon party invite <player>")
                        : Text.info("Party: " + String.join(", ", names)));
            }
        }
    }

    private void help(Player p) {
        p.sendMessage(Text.of("Dungeons", Text.GOLD));
        p.sendMessage(Text.of("/dungeon start [tier]  start a run (with your party)", Text.MUTED));
        p.sendMessage(Text.of("/dungeon leave  give up and go back", Text.MUTED));
        p.sendMessage(Text.of("/dungeon tiers  which tiers you can enter", Text.MUTED));
        p.sendMessage(Text.of("/dungeon party invite|accept|leave|list", Text.MUTED));
        p.sendMessage(Text.of("/stats  ·  /withdraw [amount]", Text.MUTED));
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("heartdungeons.admin")) {
            sender.sendMessage(Text.error("No permission."));
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "weapon" -> {
                Weapons.Type type = args.length > 2 ? Weapons.Type.byId(args[2]) : null;
                Player target = args.length > 3 ? Bukkit.getPlayerExact(args[3]) : sender instanceof Player p ? p : null;
                if (type == null || target == null) {
                    sender.sendMessage(Text.error("Usage: /dungeon admin weapon <type> [player]"));
                    return;
                }
                give(target, plugin.weapons().create(type));
                sender.sendMessage(Text.info("Gave " + type.name + " to " + target.getName() + "."));
            }
            case "heart" -> {
                int amount = args.length > 2 ? parse(args[2], 1) : 1;
                Player target = args.length > 3 ? Bukkit.getPlayerExact(args[3]) : sender instanceof Player p ? p : null;
                if (target == null) {
                    sender.sendMessage(Text.error("Usage: /dungeon admin heart [amount] [player]"));
                    return;
                }
                give(target, plugin.progress().heartItem(Math.min(64, amount)));
                sender.sendMessage(Text.info("Gave " + amount + " heart item(s) to " + target.getName() + "."));
            }
            case "setlevel" -> {
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null || args.length < 4) {
                    sender.sendMessage(Text.error("Usage: /dungeon admin setlevel <player> <level>"));
                    return;
                }
                plugin.progress().setLevel(target, parse(args[3], 0), 0);
                sender.sendMessage(Text.info(target.getName() + " is now level " + plugin.progress().level(target) + "."));
            }
            case "sethearts" -> {
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null || args.length < 4) {
                    sender.sendMessage(Text.error("Usage: /dungeon admin sethearts <player> <hearts>"));
                    return;
                }
                plugin.progress().setHearts(target, parse(args[3], 10));
                sender.sendMessage(Text.info(target.getName() + " now has " + plugin.progress().hearts(target) + " hearts."));
            }
            case "endall" -> {
                for (DungeonRun run : plugin.dungeons().runs()) plugin.dungeons().endRun(run, "An admin closed the dungeon.");
                sender.sendMessage(Text.info("All runs ended."));
            }
            default -> sender.sendMessage(Text.error("Usage: /dungeon admin <weapon|heart|setlevel|sethearts|endall>"));
        }
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void give(Player p, ItemStack item) {
        p.getInventory().addItem(item).values().forEach(left -> p.getWorld().dropItem(p.getLocation(), left));
    }

    // ------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("stats")) {
            if (args.length == 1) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (name.equals("dungeon")) {
            if (args.length == 1) {
                out.addAll(List.of("start", "leave", "tiers", "party"));
                if (sender.hasPermission("heartdungeons.admin")) out.add("admin");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
                out.addAll(List.of("1", "2", "3", "4", "5"));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("party")) {
                out.addAll(List.of("invite", "accept", "leave", "list"));
            } else if (args.length == 3 && args[0].equalsIgnoreCase("party") && args[1].equalsIgnoreCase("invite")) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
                out.addAll(List.of("weapon", "heart", "setlevel", "sethearts", "endall"));
            } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("weapon")) {
                for (Weapons.Type t : Weapons.Type.values()) out.add(t.id());
            } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")
                    && (args[1].equalsIgnoreCase("setlevel") || args[1].equalsIgnoreCase("sethearts"))) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
