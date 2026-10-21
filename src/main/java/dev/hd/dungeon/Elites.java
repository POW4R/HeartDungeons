package dev.hd.dungeon;

import dev.hd.util.Keys;
import dev.hd.util.Text;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Random;

/** Creates elite mobs and bosses scaled by tier and party size. */
public final class Elites {

    private static final TextColor[] TIER_COLORS = {
            TextColor.color(0x9BE15D), TextColor.color(0x5DC8E1), TextColor.color(0xC77DFF),
            TextColor.color(0xFF9F1C), TextColor.color(0xFF4D4D)
    };

    private Elites() {}

    public static TextColor tierColor(int tier) {
        return TIER_COLORS[Math.max(0, Math.min(4, tier - 1))];
    }

    private static String pretty(EntityType type) {
        String[] parts = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return sb.toString().trim();
    }

    private static void scale(LivingEntity e, Attribute a, double factor) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst != null) inst.setBaseValue(inst.getBaseValue() * factor);
    }

    private static void set(LivingEntity e, Attribute a, double value) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst != null) inst.setBaseValue(value);
    }

    private static void common(LivingEntity le, int runId, int tier) {
        le.getPersistentDataContainer().set(Keys.RUN, PersistentDataType.INTEGER, runId);
        le.getPersistentDataContainer().set(Keys.ELITE, PersistentDataType.INTEGER, tier);
        if (le instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
        }
        if (le instanceof Ageable ageable) ageable.setAdult();
        if (le instanceof PiglinAbstract piglin) piglin.setImmuneToZombification(true);
        if (le instanceof MagmaCube cube) cube.setSize(3);
        le.setCanPickupItems(false);
    }

    private static void gear(LivingEntity le, int tier, Random random) {
        EntityEquipment eq = le.getEquipment();
        if (eq == null) return;
        Material[][] sets = {
                {Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS, Material.STONE_SWORD},
                {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, Material.IRON_SWORD},
                {Material.IRON_HELMET, Material.DIAMOND_CHESTPLATE, Material.IRON_LEGGINGS, Material.DIAMOND_BOOTS, Material.IRON_AXE},
                {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS, Material.DIAMOND_SWORD},
                {Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS, Material.NETHERITE_SWORD}
        };
        Material[] set = sets[Math.max(0, Math.min(4, tier - 1))];
        eq.setHelmet(new ItemStack(set[0]));
        eq.setChestplate(new ItemStack(set[1]));
        eq.setLeggings(new ItemStack(set[2]));
        eq.setBoots(new ItemStack(set[3]));
        EntityType type = le.getType();
        boolean ranged = type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.PILLAGER;
        if (!ranged && type != EntityType.WITCH && type != EntityType.DROWNED && type != EntityType.BLAZE) {
            eq.setItemInMainHand(new ItemStack(set[4]));
        }
        eq.setHelmetDropChance(0);
        eq.setChestplateDropChance(0);
        eq.setLeggingsDropChance(0);
        eq.setBootsDropChance(0);
        eq.setItemInMainHandDropChance(0);
    }

    /** Spawns an elite at {@code loc}. */
    public static LivingEntity spawnElite(Location loc, EntityType type, int runId, int tier, int partySize, Random random) {
        Entity e = loc.getWorld().spawnEntity(loc, type);
        if (!(e instanceof LivingEntity le)) {
            e.remove();
            return null;
        }
        common(le, runId, tier);
        double hp = (1.5 + 0.6 * (tier - 1)) * (1 + 0.35 * (partySize - 1));
        scale(le, Attribute.MAX_HEALTH, hp);
        le.setHealth(le.getAttribute(Attribute.MAX_HEALTH).getValue());
        scale(le, Attribute.ATTACK_DAMAGE, 1 + 0.25 * tier);
        scale(le, Attribute.MOVEMENT_SPEED, 1.08);
        gear(le, tier, random);

        String affix = "";
        int roll = random.nextInt(100);
        if (roll < 12 + tier * 4) {
            switch (random.nextInt(4)) {
                case 0 -> {
                    affix = "Swift ";
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false));
                }
                case 1 -> {
                    affix = "Armored ";
                    le.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
                }
                case 2 -> {
                    affix = "Brutal ";
                    le.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0, false, false));
                }
                default -> {
                    affix = "Undying ";
                    le.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0, false, false));
                }
            }
        }
        le.customName(Text.of("☠ " + affix + "Elite " + pretty(type) + " " + "★".repeat(tier), tierColor(tier)));
        le.setCustomNameVisible(true);
        return le;
    }

    /** Spawns the run's boss in the middle of the boss room. */
    public static LivingEntity spawnBoss(Location loc, Theme theme, int runId, int tier, int partySize) {
        Entity e = loc.getWorld().spawnEntity(loc, theme.boss);
        if (!(e instanceof LivingEntity le)) {
            e.remove();
            return null;
        }
        common(le, runId, tier);
        le.getPersistentDataContainer().set(Keys.BOSS, PersistentDataType.INTEGER, tier);
        double hp = 150 * (1 + 0.5 * (tier - 1)) * (1 + 0.5 * (partySize - 1));
        set(le, Attribute.MAX_HEALTH, hp);
        le.setHealth(hp);
        scale(le, Attribute.ATTACK_DAMAGE, 1.3 + 0.3 * tier);
        set(le, Attribute.KNOCKBACK_RESISTANCE, 0.8);
        set(le, Attribute.SCALE, 1.6);
        set(le, Attribute.FOLLOW_RANGE, 40);
        gear(le, Math.min(5, tier + 1), new Random());
        EntityEquipment eq = le.getEquipment();
        if (eq != null && theme.boss != EntityType.WITHER_SKELETON) {
            eq.setItemInMainHand(new ItemStack(tier >= 3 ? Material.NETHERITE_AXE : Material.DIAMOND_AXE));
        } else if (eq != null) {
            eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        }
        le.customName(Text.of(theme.bossName, tierColor(tier)));
        le.setCustomNameVisible(true);
        return le;
    }
}
