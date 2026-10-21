package dev.hd.dungeon;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/** Visual style, mobs and boss of a dungeon run. */
public enum Theme {
    CRYPT("Forgotten Crypt",
            new Material[]{Material.DEEPSLATE_TILES, Material.CRACKED_DEEPSLATE_TILES, Material.DEEPSLATE_TILES},
            new Material[]{Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICKS, Material.POLISHED_DEEPSLATE},
            Material.POLISHED_DEEPSLATE, Material.CHISELED_DEEPSLATE, Material.PEARLESCENT_FROGLIGHT, Material.COBWEB,
            new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK, EntityType.STRAY, EntityType.ZOMBIE},
            EntityType.WITHER_SKELETON, "The Hollow King"),
    NETHER("Infernal Keep",
            new Material[]{Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.BLACKSTONE},
            new Material[]{Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, Material.NETHER_BRICKS, Material.CRACKED_NETHER_BRICKS},
            Material.NETHER_BRICKS, Material.CHISELED_NETHER_BRICKS, Material.SHROOMLIGHT, Material.SOUL_SAND,
            new EntityType[]{EntityType.WITHER_SKELETON, EntityType.BLAZE, EntityType.PIGLIN_BRUTE, EntityType.MAGMA_CUBE},
            EntityType.PIGLIN_BRUTE, "Warlord Gorrak"),
    TEMPLE("Sunken Temple",
            new Material[]{Material.PRISMARINE_BRICKS, Material.PRISMARINE, Material.DARK_PRISMARINE},
            new Material[]{Material.PRISMARINE_BRICKS, Material.PRISMARINE_BRICKS, Material.PRISMARINE},
            Material.DARK_PRISMARINE, Material.DARK_PRISMARINE, Material.SEA_LANTERN, Material.WET_SPONGE,
            new EntityType[]{EntityType.DROWNED, EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.WITCH, EntityType.DROWNED},
            EntityType.VINDICATOR, "The Tidecaller");

    public final String displayName;
    public final Material[] floor;
    public final Material[] wall;
    public final Material ceiling;
    public final Material pillar;
    public final Material light;
    public final Material decor;
    public final EntityType[] mobs;
    public final EntityType boss;
    public final String bossName;

    Theme(String displayName, Material[] floor, Material[] wall, Material ceiling, Material pillar, Material light,
          Material decor, EntityType[] mobs, EntityType boss, String bossName) {
        this.displayName = displayName;
        this.floor = floor;
        this.wall = wall;
        this.ceiling = ceiling;
        this.pillar = pillar;
        this.light = light;
        this.decor = decor;
        this.mobs = mobs;
        this.boss = boss;
        this.bossName = bossName;
    }
}
