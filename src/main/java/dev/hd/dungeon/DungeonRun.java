package dev.hd.dungeon;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.World;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** State of one dungeon run. */
public final class DungeonRun {
    public enum State { RUNNING, COMPLETED, ENDED }

    public final int id;
    public final int tier;
    public final Theme theme;
    public final World world;
    public final List<Room> rooms;
    public final Set<UUID> members = new LinkedHashSet<>();
    public final int partySize;
    public final long startedAt = System.currentTimeMillis();

    public State state = State.RUNNING;
    public long completedAt;
    public UUID boss;
    public BossBar bossBar;
    public boolean bossEnraged;
    public final Set<UUID> minions = new LinkedHashSet<>();
    public int bossTicks;

    public DungeonRun(int id, int tier, Theme theme, World world, List<Room> rooms, int partySize) {
        this.id = id;
        this.tier = tier;
        this.theme = theme;
        this.world = world;
        this.rooms = rooms;
        this.partySize = partySize;
    }

    public Room bossRoom() {
        return rooms.get(rooms.size() - 1);
    }

    public Room startRoom() {
        return rooms.get(0);
    }
}
