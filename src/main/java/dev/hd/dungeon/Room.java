package dev.hd.dungeon;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One room of a dungeon run. */
public final class Room {
    public enum Kind { START, COMBAT, BOSS }

    public final int index;
    public final Kind kind;
    public final int cx, cz;      // centre (world coordinates)
    public final int half;        // half width: room spans cx-half..cx+half
    public final int height;      // inner height
    public BlockFace entrance;    // side the players come in from (null for the start room)
    public BlockFace exit;        // side leading to the next room (null for the boss room)

    public boolean triggered;
    public boolean cleared;
    public final Set<UUID> mobs = new HashSet<>();

    public Room(int index, Kind kind, int cx, int cz, int half, int height) {
        this.index = index;
        this.kind = kind;
        this.cx = cx;
        this.cz = cz;
        this.half = half;
        this.height = height;
    }

    /** True if the location is inside the room's walls. */
    public boolean contains(Location loc, int floorY) {
        int x = loc.getBlockX(), z = loc.getBlockZ(), y = loc.getBlockY();
        return Math.abs(x - cx) < half && Math.abs(z - cz) < half && y > floorY && y <= floorY + height;
    }

    public Location center(World w, int floorY) {
        return new Location(w, cx + 0.5, floorY + 1, cz + 0.5);
    }
}
