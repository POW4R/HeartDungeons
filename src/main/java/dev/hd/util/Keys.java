package dev.hd.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Persistent-data keys. Player progress is stored on the player itself, so it survives restarts. */
public final class Keys {
    public static NamespacedKey LEVEL, XP, HEARTS, HEART_MOD, RETURN_LOC;
    public static NamespacedKey ITEM, WEAPON;
    public static NamespacedKey RUN, ELITE, BOSS;

    private Keys() {}

    public static void init(Plugin p) {
        LEVEL = new NamespacedKey(p, "level");
        XP = new NamespacedKey(p, "xp");
        HEARTS = new NamespacedKey(p, "hearts");
        HEART_MOD = new NamespacedKey(p, "hearts_bonus");
        RETURN_LOC = new NamespacedKey(p, "return_location");
        ITEM = new NamespacedKey(p, "item");
        WEAPON = new NamespacedKey(p, "weapon");
        RUN = new NamespacedKey(p, "run");
        ELITE = new NamespacedKey(p, "elite");
        BOSS = new NamespacedKey(p, "boss");
    }
}
