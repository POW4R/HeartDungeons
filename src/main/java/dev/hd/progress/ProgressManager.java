package dev.hd.progress;

import dev.hd.HeartDungeons;
import dev.hd.util.Keys;
import dev.hd.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Levels, XP and hearts. Killing hostile mobs gives XP; every few levels
 * grants +1 heart. Hearts can be withdrawn as items and given to others.
 */
public final class ProgressManager implements Listener {

    private final HeartDungeons plugin;

    public ProgressManager(HeartDungeons plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ config

    private int cfg(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    public int maxHearts() { return cfg("hearts.max", 20); }
    public int minHearts() { return cfg("hearts.min", 3); }
    public int maxLevel() { return cfg("levels.max-level", 100); }

    public int xpForNext(int level) {
        return cfg("levels.xp-base", 60) + cfg("levels.xp-per-level", 30) * level;
    }

    // ------------------------------------------------------------ data

    public int level(Player p) {
        return p.getPersistentDataContainer().getOrDefault(Keys.LEVEL, PersistentDataType.INTEGER, 0);
    }

    public int xp(Player p) {
        return p.getPersistentDataContainer().getOrDefault(Keys.XP, PersistentDataType.INTEGER, 0);
    }

    public int hearts(Player p) {
        return p.getPersistentDataContainer().getOrDefault(Keys.HEARTS, PersistentDataType.INTEGER, cfg("hearts.start", 10));
    }

    public void setLevel(Player p, int level, int xp) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(Keys.LEVEL, PersistentDataType.INTEGER, Math.max(0, Math.min(level, maxLevel())));
        pdc.set(Keys.XP, PersistentDataType.INTEGER, Math.max(0, xp));
        updateTab(p);
    }

    /** Sets hearts (clamped to 1..max) and applies the max-health change. */
    public void setHearts(Player p, int hearts) {
        int clamped = Math.max(1, Math.min(hearts, maxHearts()));
        p.getPersistentDataContainer().set(Keys.HEARTS, PersistentDataType.INTEGER, clamped);
        applyHealth(p);
    }

    public void applyHealth(Player p) {
        AttributeInstance inst = p.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        for (AttributeModifier mod : List.copyOf(inst.getModifiers())) {
            if (mod.getKey().equals(Keys.HEART_MOD)) inst.removeModifier(mod);
        }
        double bonus = (hearts(p) - 10) * 2.0;
        if (bonus != 0) {
            inst.addModifier(new AttributeModifier(Keys.HEART_MOD, bonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        if (p.getHealth() > inst.getValue()) p.setHealth(inst.getValue());
    }

    public void updateTab(Player p) {
        if (!plugin.getConfig().getBoolean("levels.show-in-tab", true)) return;
        p.playerListName(Component.text("[Lv " + level(p) + "] ", Text.GOLD).append(Component.text(p.getName())));
    }

    // ------------------------------------------------------------ XP

    public void addXp(Player p, int amount) {
        if (amount <= 0) return;
        int level = level(p);
        if (level >= maxLevel()) {
            p.sendActionBar(Text.of("+" + amount + " XP (max level)", Text.GOLD));
            return;
        }
        int xp = xp(p) + amount;
        int gainedHearts = 0;
        int perLevels = Math.max(1, cfg("hearts.per-levels", 5));
        while (level < maxLevel() && xp >= xpForNext(level)) {
            xp -= xpForNext(level);
            level++;
            if (level % perLevels == 0) gainedHearts++;
        }
        boolean leveled = level != level(p);
        setLevel(p, level, xp);
        if (leveled) {
            String sub = "";
            if (gainedHearts > 0) {
                int before = hearts(p);
                setHearts(p, before + gainedHearts);
                int got = hearts(p) - before;
                sub = got > 0 ? "+" + got + " heart" + (got > 1 ? "s" : "") + "!" : "Heart limit reached";
            }
            p.showTitle(Title.title(Text.of("Level " + level, Text.GOLD), Text.of(sub, Text.HEART)));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.2);
        } else {
            p.sendActionBar(Text.of("+" + amount + " XP  (" + xp + "/" + xpForNext(level) + ")", Text.GOLD));
        }
    }

    /** XP for a kill: based on the mob's max health, with big bonuses for elites and bosses. */
    public int xpFor(LivingEntity mob) {
        AttributeInstance hp = mob.getAttribute(Attribute.MAX_HEALTH);
        double base = hp == null ? 10 : hp.getBaseValue();
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        int xp = (int) Math.max(3, Math.round(base / 2.5));
        Integer eliteTier = pdc.get(Keys.ELITE, PersistentDataType.INTEGER);
        if (eliteTier != null) xp = (int) Math.round(xp * (1.5 + eliteTier * 0.5));
        if (pdc.has(Keys.BOSS, PersistentDataType.INTEGER)) xp *= 4;
        if (mob instanceof Boss) xp *= 3; // vanilla Wither / Ender Dragon
        return xp;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        Player killer = mob.getKiller();
        if (killer == null || mob instanceof Player) return;
        if (!(mob instanceof Enemy) && !mob.getPersistentDataContainer().has(Keys.ELITE, PersistentDataType.INTEGER)) return;
        addXp(killer, xpFor(mob));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyHealth(event.getPlayer());
        updateTab(event.getPlayer());
    }

    // ------------------------------------------------------------ Heart items

    public ItemStack heartItem(int amount) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.of("❤ Heart", Text.HEART));
        meta.lore(List.of(
                Text.of("Right-click to gain +1 max heart.", Text.MUTED),
                Text.of("Give it to a friend, or keep it safe.", Text.MUTED)));
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, "heart");
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isHeart(ItemStack item) {
        return item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()
                && "heart".equals(item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING));
    }

    /** /withdraw: turn hearts into items. Returns an error message, or null on success. */
    public String withdraw(Player p, int amount) {
        if (amount < 1) return "Amount must be at least 1.";
        int hearts = hearts(p);
        if (hearts - amount < minHearts()) {
            return "You can withdraw at most " + Math.max(0, hearts - minHearts()) + " heart(s). You must keep " + minHearts() + ".";
        }
        setHearts(p, hearts - amount);
        p.getInventory().addItem(heartItem(amount)).values()
                .forEach(left -> p.getWorld().dropItem(p.getLocation(), left));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.6f);
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseHeart(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND || !isHeart(event.getItem())) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (hearts(p) >= maxHearts()) {
            p.sendActionBar(Text.error("You already have the maximum of " + maxHearts() + " hearts."));
            return;
        }
        ItemStack item = event.getItem();
        item.setAmount(item.getAmount() - 1);
        setHearts(p, hearts(p) + 1);
        p.setHealth(Math.min(p.getHealth() + 2, p.getAttribute(Attribute.MAX_HEALTH).getValue()));
        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.4f);
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.8, 0), 8, 0.4, 0.3, 0.4);
        p.sendActionBar(Text.of("+1 heart (" + hearts(p) + "/" + maxHearts() + ")", Text.HEART));
    }

    /** Heart items can't be used in crafting (e.g. as a beacon's Nether Star). */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isHeart(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
}
