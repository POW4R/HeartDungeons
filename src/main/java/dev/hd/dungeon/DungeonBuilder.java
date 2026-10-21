package dev.hd.dungeon;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Lays out and builds a dungeon: a start room, a chain of combat rooms
 * winding east/north/south, and a large boss room, joined by corridors.
 */
public final class DungeonBuilder {

    public static final int FLOOR_Y = 64;
    private static final int SPACING = 34;
    private static final int ROOM_HALF = 7;
    private static final int BOSS_HALF = 11;
    private static final int ROOM_HEIGHT = 6;
    private static final int BOSS_HEIGHT = 10;

    private DungeonBuilder() {}

    /** Plans room positions; nothing is built yet. */
    public static List<Room> layout(int originX, int originZ, int combatRooms, Random random) {
        List<Room> rooms = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        int gx = 0, gz = 0;
        used.add(key(gx, gz));
        rooms.add(new Room(0, Room.Kind.START, originX, originZ, 5, 5));
        int total = combatRooms + 1; // + boss
        for (int i = 1; i <= total; i++) {
            BlockFace dir = pickDirection(gx, gz, used, random);
            gx += dir.getModX();
            gz += dir.getModZ();
            used.add(key(gx, gz));
            boolean boss = i == total;
            Room room = new Room(i, boss ? Room.Kind.BOSS : Room.Kind.COMBAT,
                    originX + gx * SPACING, originZ + gz * SPACING,
                    boss ? BOSS_HALF : ROOM_HALF, boss ? BOSS_HEIGHT : ROOM_HEIGHT);
            Room prev = rooms.get(rooms.size() - 1);
            prev.exit = dir;
            room.entrance = dir.getOppositeFace();
            rooms.add(room);
        }
        return rooms;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static BlockFace pickDirection(int gx, int gz, Set<Long> used, Random random) {
        List<BlockFace> options = new ArrayList<>();
        for (BlockFace f : new BlockFace[]{BlockFace.EAST, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH}) {
            if (!used.contains(key(gx + f.getModX(), gz + f.getModZ()))) options.add(f);
        }
        return options.isEmpty() ? BlockFace.EAST : options.get(random.nextInt(options.size()));
    }

    // ------------------------------------------------------------------ building

    public static void build(World w, List<Room> rooms, Theme theme, Random random) {
        for (Room room : rooms) {
            buildRoom(w, room, theme, random);
        }
        for (int i = 0; i + 1 < rooms.size(); i++) {
            buildCorridor(w, rooms.get(i), rooms.get(i + 1), theme, random);
        }
        // Doors: every exit starts closed (opened when the room is cleared). The start room's exit opens right away.
        for (Room room : rooms) {
            if (room.exit != null) setDoor(w, room, room.exit, room.kind != Room.Kind.START);
        }
    }

    private static Material pick(Material[] options, Random random) {
        return options[random.nextInt(options.length)];
    }

    private static void buildRoom(World w, Room r, Theme t, Random random) {
        int y0 = FLOOR_Y;
        int top = y0 + r.height + 1;
        for (int x = r.cx - r.half; x <= r.cx + r.half; x++) {
            for (int z = r.cz - r.half; z <= r.cz + r.half; z++) {
                boolean edge = Math.abs(x - r.cx) == r.half || Math.abs(z - r.cz) == r.half;
                w.getBlockAt(x, y0, z).setType(pick(t.floor, random), false);
                w.getBlockAt(x, y0 - 1, z).setType(Material.BEDROCK, false);
                for (int y = y0 + 1; y < top; y++) {
                    w.getBlockAt(x, y, z).setType(edge ? pick(t.wall, random) : Material.AIR, false);
                }
                boolean lightSpot = !edge && (x - r.cx) % 4 == 0 && (z - r.cz) % 4 == 0;
                w.getBlockAt(x, top, z).setType(lightSpot ? t.light : t.ceiling, false);
            }
        }
        // Corner pillars and decoration
        if (r.kind != Room.Kind.START) {
            int p = r.half - 2;
            for (int[] c : new int[][]{{p, p}, {p, -p}, {-p, p}, {-p, -p}}) {
                for (int y = y0 + 1; y < top; y++) {
                    w.getBlockAt(r.cx + c[0], y, r.cz + c[1]).setType(t.pillar, false);
                }
            }
            for (int i = 0; i < r.half; i++) {
                int x = r.cx + random.nextInt(r.half * 2 - 1) - r.half + 1;
                int z = r.cz + random.nextInt(r.half * 2 - 1) - r.half + 1;
                Block b = w.getBlockAt(x, top - 1, z);
                if (b.isEmpty() && random.nextBoolean()) b.setType(t.decor, false);
            }
        }
        if (r.kind == Room.Kind.START) {
            w.getBlockAt(r.cx, y0, r.cz).setType(Material.LODESTONE, false);
        }
        if (r.kind == Room.Kind.BOSS) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    w.getBlockAt(r.cx + x, y0, r.cz + z).setType(Math.abs(x) == 2 || Math.abs(z) == 2 ? t.pillar : Material.CRYING_OBSIDIAN, false);
                }
            }
        }
    }

    private static void buildCorridor(World w, Room a, Room b, Theme t, Random random) {
        BlockFace dir = a.exit;
        boolean alongX = dir.getModX() != 0;
        int start = (alongX ? a.cx : a.cz) + (alongX ? dir.getModX() : dir.getModZ()) * a.half;
        int end = (alongX ? b.cx : b.cz) - (alongX ? dir.getModX() : dir.getModZ()) * b.half;
        int step = Integer.signum(end - start);
        int line = alongX ? a.cz : a.cx;
        int y0 = FLOOR_Y;
        for (int i = start; i != end + step; i += step) {
            for (int s = -2; s <= 2; s++) {
                int x = alongX ? i : line + s;
                int z = alongX ? line + s : i;
                boolean side = Math.abs(s) == 2;
                boolean wallEnd = i == start || i == end; // inside a room wall: only the doorway is cut
                w.getBlockAt(x, y0, z).setType(pick(t.floor, random), false);
                w.getBlockAt(x, y0 - 1, z).setType(Material.BEDROCK, false);
                for (int y = y0 + 1; y <= y0 + 3; y++) {
                    if (wallEnd) {
                        if (!side) w.getBlockAt(x, y, z).setType(Material.AIR, false);
                    } else {
                        w.getBlockAt(x, y, z).setType(side ? pick(t.wall, random) : Material.AIR, false);
                    }
                }
                if (!wallEnd) {
                    boolean light = s == 0 && Math.floorMod(i, 5) == 0;
                    w.getBlockAt(x, y0 + 4, z).setType(light ? t.light : t.ceiling, false);
                }
            }
        }
    }

    /** Fills (closed) or clears (open) the 3x3 doorway on one side of a room. */
    public static void setDoor(World w, Room r, BlockFace side, boolean closed) {
        boolean alongX = side.getModX() != 0;
        int wallCoord = (alongX ? r.cx : r.cz) + (alongX ? side.getModX() : side.getModZ()) * r.half;
        int line = alongX ? r.cz : r.cx;
        for (int s = -1; s <= 1; s++) {
            for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 3; y++) {
                Block b = alongX ? w.getBlockAt(wallCoord, y, line + s) : w.getBlockAt(line + s, y, wallCoord);
                b.setType(closed ? Material.IRON_BARS : Material.AIR, true);
            }
        }
    }
}
