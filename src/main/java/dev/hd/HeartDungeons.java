package dev.hd;

import dev.hd.dungeon.DungeonManager;
import dev.hd.progress.ProgressManager;
import dev.hd.util.Keys;
import dev.hd.weapon.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * HeartDungeons: kill mobs for XP, level up for hearts, trade hearts as items,
 * and run generated dungeons with elites, bosses and OP weapons.
 */
public final class HeartDungeons extends JavaPlugin {

    private ProgressManager progress;
    private Weapons weapons;
    private DungeonManager dungeons;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.init(this);

        progress = new ProgressManager(this);
        weapons = new Weapons(this);
        dungeons = new DungeonManager(this);
        dungeons.createWorld();

        getServer().getPluginManager().registerEvents(progress, this);
        getServer().getPluginManager().registerEvents(weapons, this);
        getServer().getPluginManager().registerEvents(dungeons, this);

        Commands handler = new Commands(this);
        for (String name : new String[]{"dungeon", "stats", "withdraw"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(handler);
                cmd.setTabCompleter(handler);
            }
        }

        Bukkit.getScheduler().runTaskTimer(this, dungeons::tick, 40L, 20L);

        // Players already online (e.g. after a reload) get their hearts applied.
        for (Player p : Bukkit.getOnlinePlayers()) {
            progress.applyHealth(p);
            progress.updateTab(p);
        }
    }

    @Override
    public void onDisable() {
        if (dungeons != null) dungeons.shutdown();
    }

    public ProgressManager progress() {
        return progress;
    }

    public Weapons weapons() {
        return weapons;
    }

    public DungeonManager dungeons() {
        return dungeons;
    }
}
