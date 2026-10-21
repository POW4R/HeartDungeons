package dev.hd.weapon;

import dev.hd.HeartDungeons;
import dev.hd.util.Keys;
import dev.hd.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The OP weapons, dropped by dungeon bosses. */
public final class Weapons implements Listener {

    public enum Type {
        VAMPIRE_BLADE(Material.NETHERITE_SWORD, "Vampire Blade", Text.HEART, 10, 1.6,
                "Heals you for 25% of the damage you deal."),
        STORMBREAKER(Material.MACE, "Stormbreaker", TextColor.color(0x7FD3FF), 0, 0,
                "Right-click: call lightning where you look", "(8 damage around the strike, 10s cooldown)."),
        REAPERS_SCYTHE(Material.NETHERITE_HOE, "Reaper's Scythe", Text.EPIC, 11, 1.2,
                "Hits also cut every enemy within 4 blocks", "for half damage and wither them."),
        INFERNO_BOW(Material.BOW, "Inferno Bow", Text.LEGEND, 0, 0,
                "Arrows burst into flame on impact", "(no block damage)."),
        FROSTBITE_TRIDENT(Material.TRIDENT, "Frostbite Trident", TextColor.color(0xBDF2FF), 0, 0,
                "Hits freeze and heavily slow the target.");

        public final Material base;
        public final String name;
        public final TextColor color;
        public final double damage;   // total attack damage (0 = keep vanilla)
        public final double speed;    // attacks per second (0 = keep vanilla)
        public final String[] lore;

        Type(Material base, String name, TextColor color, double damage, double speed, String... lore) {
            this.base = base;
            this.name = name;
            this.color = color;
            this.damage = damage;
            this.speed = speed;
            this.lore = lore;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Type byId(String id) {
            for (Type t : values()) if (t.id().equalsIgnoreCase(id)) return t;
            return null;
        }
    }

    private final HeartDungeons plugin;
    private boolean sweeping; // stops the scythe sweep from triggering itself

    public Weapons(HeartDungeons plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(Type type) {
        ItemStack item = new ItemStack(type.base);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.of(type.name, type.color));
        List<Component> lore = new ArrayList<>();
        lore.add(Text.of("Dungeon Weapon", Text.GOLD));
        for (String l : type.lore) lore.add(Text.of(l, Text.MUTED));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.setUnbreakable(false);
        if (type.damage > 0) {
            // Player base attack damage is 1 and base speed is 4, so modifiers are relative to that.
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    new NamespacedKey(plugin, "weapon_damage"), type.damage - 1, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    new NamespacedKey(plugin, "weapon_speed"), type.speed - 4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        meta.getPersistentDataContainer().set(Keys.WEAPON, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        if (type == Type.FROSTBITE_TRIDENT) {
            item.addUnsafeEnchantment(Enchantment.LOYALTY, 3);
            item.addUnsafeEnchantment(Enchantment.IMPALING, 3);
        }
        if (type == Type.INFERNO_BOW) {
            item.addUnsafeEnchantment(Enchantment.POWER, 4);
        }
        if (type == Type.STORMBREAKER) {
            item.addUnsafeEnchantment(Enchantment.DENSITY, 3);
        }
        return item;
    }

    public static Type typeOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(Keys.WEAPON, PersistentDataType.STRING);
        return id == null ? null : Type.byId(id);
    }

    private static boolean friendly(Player attacker, Entity e) {
        if (e == attacker || !(e instanceof LivingEntity) || e.isDead()) return true;
        if (e instanceof Tameable t && attacker.equals(t.getOwner())) return true;
        return e instanceof Player p && p.getGameMode() != org.bukkit.GameMode.SURVIVAL && p.getGameMode() != org.bukkit.GameMode.ADVENTURE;
    }

    // ------------------------------------------------------------ melee

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)) return;
        Type type = typeOf(player.getInventory().getItemInMainHand());
        if (type == null) return;
        double dealt = event.getFinalDamage();
        switch (type) {
            case VAMPIRE_BLADE -> {
                AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
                double cap = max == null ? 20 : max.getValue();
                player.setHealth(Math.min(cap, player.getHealth() + dealt * 0.25));
                target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(0xB0001E), 1.3f));
            }
            case REAPERS_SCYTHE -> {
                if (sweeping) return;
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
                sweeping = true;
                try {
                    for (Entity near : target.getNearbyEntities(4, 2, 4)) {
                        if (near == target || friendly(player, near) || near instanceof Player) continue; // never hits players
                        LivingEntity le = (LivingEntity) near;
                        le.damage(dealt * 0.5, player);
                        le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
                    }
                } finally {
                    sweeping = false;
                }
                target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 6, 1.5, 0.3, 1.5);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ Stormbreaker

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (typeOf(item) != Type.STORMBREAKER) return;
        event.setCancelled(true);
        if (player.hasCooldown(item)) return;
        Block block = player.getTargetBlockExact(30, FluidCollisionMode.NEVER);
        Location at = block != null ? block.getLocation().add(0.5, 1, 0.5)
                : player.getEyeLocation().add(player.getLocation().getDirection().multiply(20));
        at.getWorld().strikeLightningEffect(at);
        for (Entity e : at.getWorld().getNearbyEntities(at, 3, 3, 3)) {
            if (friendly(player, e)) continue;
            ((LivingEntity) e).damage(8, player);
            e.setFireTicks(60);
        }
        player.setCooldown(item, 200);
    }

    // ------------------------------------------------------------ projectiles

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player && typeOf(event.getBow()) == Type.INFERNO_BOW && event.getProjectile() instanceof Arrow arrow) {
            arrow.getPersistentDataContainer().set(Keys.WEAPON, PersistentDataType.STRING, Type.INFERNO_BOW.id());
            arrow.setFireTicks(200);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onThrow(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Trident trident && event.getEntity().getShooter() instanceof Player
                && typeOf(trident.getItemStack()) == Type.FROSTBITE_TRIDENT) {
            trident.getPersistentDataContainer().set(Keys.WEAPON, PersistentDataType.STRING, Type.FROSTBITE_TRIDENT.id());
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        String id = proj.getPersistentDataContainer().get(Keys.WEAPON, PersistentDataType.STRING);
        if (id == null || !(proj.getShooter() instanceof Player shooter)) return;
        Type type = Type.byId(id);
        if (type == Type.INFERNO_BOW) {
            Location at = proj.getLocation();
            at.getWorld().createExplosion(at, 1.6f, false, false, shooter);
            at.getWorld().spawnParticle(Particle.FLAME, at, 25, 0.6, 0.6, 0.6, 0.05);
            for (Entity e : at.getWorld().getNearbyEntities(at, 2.5, 2.5, 2.5)) {
                if (!friendly(shooter, e)) e.setFireTicks(100);
            }
            proj.remove();
        } else if (type == Type.FROSTBITE_TRIDENT && event.getHitEntity() instanceof LivingEntity target) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 3));
            target.setFreezeTicks(Math.max(target.getFreezeTicks(), 200));
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.6f);
        }
    }
}
