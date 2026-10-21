package dev.hd.dungeon;

import dev.hd.HeartDungeons;
import dev.hd.util.Keys;
import dev.hd.util.Text;
import dev.hd.weapon.Weapons;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** Dungeon world, parties, runs, rooms, bosses and rewards. */
public final class DungeonManager implements Listener {

    public static final String WORLD_NAME = "heartdungeons_instances";
    private static final int MAX_PARTY = 5;

    private final HeartDungeons plugin;
    private final Random random = new Random();
    private World world;
    private int nextRunId = 1;
    private final Map<Integer, DungeonRun> runs = new HashMap<>();
    private final Map<UUID, DungeonRun> runByPlayer = new HashMap<>();

    // Parties
    private static final class Party {
        UUID leader;
        final Set<UUID> members = new LinkedHashSet<>();
    }

    private final Map<UUID, Party> partyOf = new HashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>(); // invitee -> leader

    public DungeonManager(HeartDungeons plugin) {
        this.plugin = plugin;
    }

    // =================================================================== world

    /** The instance world is temporary: it's wiped on every start, so old runs never pile up. */
    public void createWorld() {
        File folder = new File(Bukkit.getWorldContainer(), WORLD_NAME);
        if (Bukkit.getWorld(WORLD_NAME) == null && folder.exists()) {
            try (Stream<Path> walk = Files.walk(folder.toPath())) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException e) {
                plugin.getLogger().warning("Could not wipe old dungeon world: " + e.getMessage());
            }
        }
        world = new WorldCreator(WORLD_NAME).environment(World.Environment.NORMAL).generator(new VoidGenerator()).createWorld();
        if (world != null) {
            world.setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false);
            world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);
            world.setGameRule(org.bukkit.GameRules.ADVANCE_WEATHER, false);
            world.setGameRule(org.bukkit.GameRules.MOB_GRIEFING, false);
            world.setTime(18000);
            world.setDifficulty(org.bukkit.Difficulty.HARD);
        }
    }

    public World world() {
        return world;
    }

    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
            return new BiomeProvider() {
                @Override
                public @NotNull Biome getBiome(@NotNull WorldInfo info, int x, int y, int z) {
                    return Biome.THE_VOID;
                }

                @Override
                public @NotNull List<Biome> getBiomes(@NotNull WorldInfo info) {
                    return List.of(Biome.THE_VOID);
                }
            };
        }

        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }

    // =================================================================== parties

    public String invite(Player leader, Player target) {
        if (target == leader) return "You can't invite yourself.";
        Party party = partyOf.get(leader.getUniqueId());
        if (party != null && !party.leader.equals(leader.getUniqueId())) return "Only the party leader can invite.";
        if (party != null && party.members.size() >= MAX_PARTY) return "Parties can have at most " + MAX_PARTY + " players.";
        if (partyOf.containsKey(target.getUniqueId())) return target.getName() + " is already in a party.";
        invites.put(target.getUniqueId(), leader.getUniqueId());
        target.sendMessage(Text.info(leader.getName() + " invited you to their dungeon party. Type ")
                .append(Text.of("/dungeon party accept", Text.GOLD)));
        return null;
    }

    public String accept(Player player) {
        UUID leaderId = invites.remove(player.getUniqueId());
        Player leader = leaderId == null ? null : Bukkit.getPlayer(leaderId);
        if (leader == null) return "You have no pending invite.";
        Party party = partyOf.get(leaderId);
        if (party == null) {
            party = new Party();
            party.leader = leaderId;
            party.members.add(leaderId);
            partyOf.put(leaderId, party);
        }
        if (party.members.size() >= MAX_PARTY) return "That party is full.";
        party.members.add(player.getUniqueId());
        partyOf.put(player.getUniqueId(), party);
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) m.sendMessage(Text.info(player.getName() + " joined the party."));
        }
        return null;
    }

    public void leaveParty(Player player) {
        Party party = partyOf.remove(player.getUniqueId());
        if (party == null) {
            player.sendMessage(Text.info("You're not in a party."));
            return;
        }
        party.members.remove(player.getUniqueId());
        if (party.leader.equals(player.getUniqueId()) && !party.members.isEmpty()) {
            party.leader = party.members.iterator().next();
        }
        if (party.members.size() <= 1) {
            for (UUID id : party.members) partyOf.remove(id);
        }
        player.sendMessage(Text.info("You left the party."));
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) m.sendMessage(Text.info(player.getName() + " left the party."));
        }
    }

    public List<String> partyNames(Player player) {
        Party party = partyOf.get(player.getUniqueId());
        List<String> names = new ArrayList<>();
        if (party == null) return names;
        for (UUID id : party.members) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            names.add((id.equals(party.leader) ? "★ " : "") + (n == null ? "?" : n));
        }
        return names;
    }

    // =================================================================== starting

    public int tierLevel(int tier) {
        if (tier <= 1) return 0;
        return plugin.getConfig().getInt("dungeons.tier-levels." + tier, (tier - 1) * 10);
    }

    public boolean inRun(Player p) {
        return runByPlayer.containsKey(p.getUniqueId());
    }

    public String start(Player leader, int tier) {
        if (world == null) return "The dungeon world isn't available.";
        if (tier < 1 || tier > 5) return "Tier must be 1-5.";
        if (inRun(leader)) return "You're already in a dungeon.";
        if (leader.getWorld().equals(world)) return "You're already in the dungeon world.";
        Party party = partyOf.get(leader.getUniqueId());
        if (party != null && !party.leader.equals(leader.getUniqueId())) return "Only the party leader can start a run.";
        int need = tierLevel(tier);
        if (plugin.progress().level(leader) < need) return "Tier " + tier + " needs level " + need + ".";

        List<Player> players = new ArrayList<>();
        players.add(leader);
        if (party != null) {
            for (UUID id : party.members) {
                Player m = Bukkit.getPlayer(id);
                if (m == null || m == leader || inRun(m) || m.isDead()) continue;
                players.add(m);
            }
        }

        int id = nextRunId++;
        Theme theme = Theme.values()[random.nextInt(Theme.values().length)];
        int rooms = Math.max(1, Math.min(10, plugin.getConfig().getInt("dungeons.rooms", 5)));
        List<Room> layout = DungeonBuilder.layout(id * 1000, 0, rooms, random);
        DungeonBuilder.build(world, layout, theme, random);
        DungeonRun run = new DungeonRun(id, tier, theme, world, layout, players.size());
        runs.put(id, run);

        Room start = run.startRoom();
        Location spawn = start.center(world, DungeonBuilder.FLOOR_Y);
        BlockFace exit = start.exit;
        spawn.setYaw(switch (exit) {
            case NORTH -> 180f;
            case SOUTH -> 0f;
            case WEST -> 90f;
            default -> -90f;
        });
        for (Player p : players) {
            saveReturn(p);
            run.members.add(p.getUniqueId());
            runByPlayer.put(p.getUniqueId(), run);
            p.teleportAsync(spawn).thenAccept(ok -> {
                if (!ok) return;
                p.showTitle(Title.title(Text.of(theme.displayName, Elites.tierColor(tier)),
                        Text.of("Tier " + tier + "  ·  " + rooms + " rooms and a boss", Text.MUTED)));
                p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.8f, 1.2f);
                p.sendMessage(Text.info("Dying in here costs " + plugin.getConfig().getInt("hearts.lost-on-dungeon-death", 1)
                        + " heart. You keep your items. /dungeon leave to give up."));
            });
        }
        return null;
    }

    // =================================================================== return locations

    private void saveReturn(Player p) {
        Location l = p.getLocation();
        String s = l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
        p.getPersistentDataContainer().set(Keys.RETURN_LOC, PersistentDataType.STRING, s);
    }

    private Location readReturn(Player p) {
        String s = p.getPersistentDataContainer().get(Keys.RETURN_LOC, PersistentDataType.STRING);
        World fallback = Bukkit.getWorlds().get(0);
        if (s == null) return null;
        try {
            String[] parts = s.split(";");
            World w = Bukkit.getWorld(parts[0]);
            if (w == null) return fallback.getSpawnLocation();
            return new Location(w, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (RuntimeException e) {
            return fallback.getSpawnLocation();
        }
    }

    /** Sends a player back to where they entered (or to spawn). */
    public void sendBack(Player p, boolean sync) {
        Location back = readReturn(p);
        if (back == null) back = Bukkit.getWorlds().get(0).getSpawnLocation();
        p.getPersistentDataContainer().remove(Keys.RETURN_LOC);
        if (sync) p.teleport(back);
        else p.teleportAsync(back);
    }

    // =================================================================== leaving / ending

    /** Removes a player from their run (no penalty) and sends them back. */
    public void leave(Player p) {
        DungeonRun run = runByPlayer.remove(p.getUniqueId());
        if (run == null) {
            p.sendMessage(Text.info("You're not in a dungeon."));
            return;
        }
        run.members.remove(p.getUniqueId());
        if (run.bossBar != null) p.hideBossBar(run.bossBar);
        sendBack(p, false);
        p.sendMessage(Text.info("You left the dungeon."));
        if (run.members.isEmpty() && run.state != DungeonRun.State.ENDED) endRun(run, null);
    }

    public void endRun(DungeonRun run, String message) {
        if (run.state == DungeonRun.State.ENDED) return;
        run.state = DungeonRun.State.ENDED;
        runs.remove(run.id);
        for (Entity e : world.getEntities()) {
            Integer id = e.getPersistentDataContainer().get(Keys.RUN, PersistentDataType.INTEGER);
            if (id != null && id == run.id) e.remove();
        }
        for (UUID id : new ArrayList<>(run.members)) {
            runByPlayer.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (run.bossBar != null) p.hideBossBar(run.bossBar);
            if (message != null) p.sendMessage(Text.info(message));
            if (!p.isDead()) sendBack(p, false);
        }
        run.members.clear();
    }

    public void shutdown() {
        for (DungeonRun run : new ArrayList<>(runs.values())) {
            for (UUID id : run.members) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && !p.isDead()) sendBack(p, true);
            }
        }
        runs.clear();
        runByPlayer.clear();
    }

    public List<DungeonRun> runs() {
        return new ArrayList<>(runs.values());
    }

    // =================================================================== ticking (every second)

    public void tick() {
        long now = System.currentTimeMillis();
        long limit = plugin.getConfig().getInt("dungeons.time-limit-minutes", 40) * 60_000L;
        long exitDelay = plugin.getConfig().getInt("dungeons.exit-delay-seconds", 90) * 1000L;
        for (DungeonRun run : new ArrayList<>(runs.values())) {
            run.members.removeIf(id -> Bukkit.getPlayer(id) == null);
            if (run.members.isEmpty()) {
                endRun(run, null);
                continue;
            }
            if (run.state == DungeonRun.State.COMPLETED) {
                long left = (run.completedAt + exitDelay - now) / 1000;
                if (left <= 0) endRun(run, "The dungeon fades away. Welcome back!");
                else if (left % 15 == 0 || left <= 5) forMembers(run, p -> p.sendActionBar(Text.of("Leaving in " + left + "s", Text.GOLD)));
                continue;
            }
            if (now - run.startedAt > limit) {
                endRun(run, "Time's up! The dungeon collapses.");
                continue;
            }
            for (Room room : run.rooms) roomTick(run, room);
            bossTick(run);
        }
    }

    private void forMembers(DungeonRun run, java.util.function.Consumer<Player> action) {
        for (UUID id : run.members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) action.accept(p);
        }
    }

    private void roomTick(DungeonRun run, Room room) {
        if (room.kind == Room.Kind.START) return;
        if (!room.triggered) {
            for (UUID id : run.members) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.getWorld().equals(world) && room.contains(p.getLocation(), DungeonBuilder.FLOOR_Y)) {
                    trigger(run, room);
                    break;
                }
            }
            return;
        }
        if (room.cleared) return;
        // Keep mobs inside and count survivors.
        Location center = room.center(world, DungeonBuilder.FLOOR_Y);
        room.mobs.removeIf(uuid -> {
            Entity e = Bukkit.getEntity(uuid);
            if (e == null || e.isDead() || !e.isValid()) return true;
            if (!room.contains(e.getLocation(), DungeonBuilder.FLOOR_Y)) e.teleport(center);
            return false;
        });
        if (room.kind == Room.Kind.COMBAT && room.mobs.isEmpty()) {
            room.cleared = true;
            if (room.exit != null) DungeonBuilder.setDoor(world, room, room.exit, false);
            world.playSound(center, Sound.BLOCK_IRON_DOOR_OPEN, 1.5f, 0.7f);
            int combat = run.rooms.size() - 2;
            forMembers(run, p -> p.sendActionBar(Text.of("Room " + room.index + "/" + combat + " cleared. The way forward is open.", Text.GOLD)));
            if (random.nextInt(100) < 35) supplyChest(center.getBlock());
        }
    }

    private void trigger(DungeonRun run, Room room) {
        room.triggered = true;
        int combat = run.rooms.size() - 2;
        if (room.kind == Room.Kind.BOSS) {
            forMembers(run, p -> p.showTitle(Title.title(Text.of(run.theme.bossName, Elites.tierColor(run.tier)),
                    Text.of("awakens...", Text.MUTED))));
            world.playSound(room.center(world, DungeonBuilder.FLOOR_Y), Sound.ENTITY_WITHER_SPAWN, 1f, 0.8f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> spawnBoss(run, room), 50L);
            return;
        }
        int count = Math.min(14, 3 + run.tier + (run.partySize - 1) * 2);
        forMembers(run, p -> p.sendActionBar(Text.of("Room " + room.index + "/" + combat + ": " + count + " elites", Elites.tierColor(run.tier))));
        for (int i = 0; i < count; i++) {
            Location at = randomSpot(room);
            var type = run.theme.mobs[random.nextInt(run.theme.mobs.length)];
            LivingEntity le = Elites.spawnElite(at, type, run.id, run.tier, run.partySize, random);
            if (le != null) {
                room.mobs.add(le.getUniqueId());
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.02);
            }
        }
    }

    private Location randomSpot(Room room) {
        int range = room.half - 2;
        for (int i = 0; i < 20; i++) {
            int x = room.cx + random.nextInt(range * 2 + 1) - range;
            int z = room.cz + random.nextInt(range * 2 + 1) - range;
            if (room.entrance != null) {
                int dx = x - (room.cx + room.entrance.getModX() * room.half);
                int dz = z - (room.cz + room.entrance.getModZ() * room.half);
                if (dx * dx + dz * dz < 16) continue; // not right at the door
            }
            Block b = world.getBlockAt(x, DungeonBuilder.FLOOR_Y + 1, z);
            if (b.isEmpty() && b.getRelative(0, 1, 0).isEmpty()) return b.getLocation().add(0.5, 0, 0.5);
        }
        return room.center(world, DungeonBuilder.FLOOR_Y);
    }

    // =================================================================== boss

    private void spawnBoss(DungeonRun run, Room room) {
        if (run.state != DungeonRun.State.RUNNING) return;
        LivingEntity boss = Elites.spawnBoss(room.center(world, DungeonBuilder.FLOOR_Y), run.theme, run.id, run.tier, run.partySize);
        if (boss == null) return;
        run.boss = boss.getUniqueId();
        run.bossBar = BossBar.bossBar(Text.of(run.theme.bossName, Elites.tierColor(run.tier)), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        forMembers(run, p -> p.showBossBar(run.bossBar));
        world.spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), 1);
    }

    private void bossTick(DungeonRun run) {
        if (run.boss == null) return;
        Entity e = Bukkit.getEntity(run.boss);
        if (!(e instanceof LivingEntity boss) || boss.isDead()) return;
        Room room = run.bossRoom();
        Location center = room.center(world, DungeonBuilder.FLOOR_Y);
        if (!room.contains(boss.getLocation(), DungeonBuilder.FLOOR_Y)) boss.teleport(center);
        run.bossTicks++;
        updateBossBar(run, boss);

        List<Player> targets = new ArrayList<>();
        for (UUID id : run.members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !p.isDead() && p.getWorld().equals(world) && room.contains(p.getLocation(), DungeonBuilder.FLOOR_Y)
                    && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) {
                targets.add(p);
            }
        }
        if (targets.isEmpty()) return;

        double max = boss.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (!run.bossEnraged && boss.getHealth() < max / 2) {
            run.bossEnraged = true;
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0));
            world.playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.6f);
            forMembers(run, p -> p.sendActionBar(Text.of(run.theme.bossName + " is enraged!", Text.HEART)));
        }
        int t = run.bossTicks;
        int speedUp = run.bossEnraged ? 2 : 0;

        // Shockwave
        if (t % (9 - speedUp) == 0) {
            world.spawnParticle(Particle.EXPLOSION, boss.getLocation(), 8, 2.5, 0.3, 2.5);
            world.playSound(boss.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
            for (Player p : targets) {
                if (p.getLocation().distanceSquared(boss.getLocation()) > 36) continue;
                Vector away = p.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
                if (away.lengthSquared() < 0.01) away = new Vector(1, 0, 0);
                p.damage(4 + run.tier, boss);
                p.setVelocity(away.normalize().multiply(1.4).setY(0.6));
            }
        }
        // Summon minions
        if (t % (14 - speedUp) == 0) {
            run.minions.removeIf(id -> {
                Entity m = Bukkit.getEntity(id);
                return m == null || m.isDead();
            });
            int toSpawn = Math.min(6 - run.minions.size(), 1 + run.tier / 2 + (run.partySize - 1));
            for (int i = 0; i < toSpawn; i++) {
                Location at = randomSpot(room);
                LivingEntity minion = Elites.spawnElite(at, run.theme.mobs[random.nextInt(run.theme.mobs.length)],
                        run.id, Math.max(1, run.tier - 1), 1, random);
                if (minion != null) run.minions.add(minion.getUniqueId());
                world.spawnParticle(Particle.SOUL, at.clone().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.02);
            }
            if (toSpawn > 0) world.playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.5f, 0.8f);
        }
        // Fireball volley (tier 3+, or always in the Infernal Keep)
        if ((run.tier >= 3 || run.theme == Theme.NETHER) && t % (7 - speedUp / 2) == 0) {
            for (Player p : targets) {
                Vector dir = p.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector()).normalize();
                SmallFireball fb = boss.launchProjectile(SmallFireball.class, dir.multiply(0.9));
                fb.setIsIncendiary(false);
                fb.getPersistentDataContainer().set(Keys.RUN, PersistentDataType.INTEGER, run.id);
            }
            world.playSound(boss.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.7f);
        }
    }

    private void updateBossBar(DungeonRun run, LivingEntity boss) {
        if (run.bossBar == null) return;
        double max = boss.getAttribute(Attribute.MAX_HEALTH).getValue();
        run.bossBar.progress((float) Math.max(0, Math.min(1, boss.getHealth() / max)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity le) || !le.getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.INTEGER)) return;
        Integer id = le.getPersistentDataContainer().get(Keys.RUN, PersistentDataType.INTEGER);
        DungeonRun run = id == null ? null : runs.get(id);
        if (run != null) Bukkit.getScheduler().runTask(plugin, () -> updateBossBar(run, le));
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity le = event.getEntity();
        Integer id = le.getPersistentDataContainer().get(Keys.RUN, PersistentDataType.INTEGER);
        if (id == null) return;
        DungeonRun run = runs.get(id);
        event.setDroppedExp(event.getDroppedExp() * 2);
        if (run == null || !le.getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.INTEGER)) return;
        complete(run, le.getLocation());
    }

    private void complete(DungeonRun run, Location where) {
        run.state = DungeonRun.State.COMPLETED;
        run.completedAt = System.currentTimeMillis();
        for (UUID m : run.minions) {
            Entity e = Bukkit.getEntity(m);
            if (e != null) e.remove();
        }
        Location center = run.bossRoom().center(world, DungeonBuilder.FLOOR_Y);
        bossChest(center.getBlock(), run.tier);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center.clone().add(0, 2, 0), 120, 1.5, 1.5, 1.5, 0.4);
        int xp = 120 * run.tier;
        List<String> names = new ArrayList<>();
        forMembers(run, p -> {
            if (run.bossBar != null) p.hideBossBar(run.bossBar);
            names.add(p.getName());
            p.showTitle(Title.title(Text.of("Victory!", Text.GOLD), Text.of("The loot chest is in the middle of the room", Text.MUTED)));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            plugin.progress().addXp(p, xp);
        });
        Bukkit.broadcast(Text.info(String.join(", ", names) + " defeated " + run.theme.bossName
                + " (Tier " + run.tier + " " + run.theme.displayName + ")!"));
    }

    // =================================================================== loot

    private void bossChest(Block block, int tier) {
        block.setType(Material.CHEST);
        if (!(block.getState(false) instanceof Chest chest)) return;
        Inventory inv = chest.getBlockInventory();
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Material.DIAMOND, 2 + random.nextInt(2 * tier + 1)));
        loot.add(new ItemStack(Material.EMERALD, 4 + random.nextInt(4 * tier + 1)));
        loot.add(new ItemStack(Material.GOLDEN_APPLE, 2 + random.nextInt(3)));
        loot.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 8 * tier));
        if (tier >= 3) loot.add(new ItemStack(Material.NETHERITE_SCRAP, 1 + random.nextInt(tier - 1)));
        if (tier >= 4 && random.nextDouble() < 0.25) loot.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
        if (random.nextDouble() < 0.15 + 0.12 * tier) loot.add(plugin.progress().heartItem(1));
        if (random.nextDouble() < 0.08 + 0.12 * tier) {
            Weapons.Type[] types = Weapons.Type.values();
            loot.add(plugin.weapons().create(types[random.nextInt(types.length)]));
        }
        fill(inv, loot);
    }

    private void supplyChest(Block block) {
        if (!block.isEmpty()) return;
        block.setType(Material.CHEST);
        if (!(block.getState(false) instanceof Chest chest)) return;
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Material.COOKED_BEEF, 4 + random.nextInt(5)));
        loot.add(new ItemStack(Material.ARROW, 16));
        loot.add(new ItemStack(Material.GOLDEN_CARROT, 2 + random.nextInt(3)));
        ItemStack potion = new ItemStack(Material.POTION);
        if (potion.getItemMeta() instanceof PotionMeta meta) {
            meta.setBasePotionType(PotionType.HEALING);
            potion.setItemMeta(meta);
        }
        loot.add(potion);
        fill(chest.getBlockInventory(), loot);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.2f);
    }

    private void fill(Inventory inv, List<ItemStack> loot) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) slots.add(i);
        Collections.shuffle(slots, random);
        for (int i = 0; i < loot.size() && i < slots.size(); i++) inv.setItem(slots.get(i), loot.get(i));
    }

    // =================================================================== players

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        DungeonRun run = runByPlayer.remove(p.getUniqueId());
        if (run == null) return;
        run.members.remove(p.getUniqueId());
        if (run.bossBar != null) p.hideBossBar(run.bossBar);

        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);

        int lose = plugin.getConfig().getInt("hearts.lost-on-dungeon-death", 1);
        int before = plugin.progress().hearts(p);
        int after = Math.max(plugin.progress().minHearts(), before - lose);
        if (after < before) {
            plugin.progress().setHearts(p, after);
            p.sendMessage(Text.error("You died in the dungeon and lost " + (before - after) + " heart"
                    + (before - after > 1 ? "s" : "") + ". (" + after + " left)"));
        } else {
            p.sendMessage(Text.info("You died in the dungeon. You're at the minimum hearts, so none were lost."));
        }
        forMembers(run, m -> m.sendMessage(Text.info(p.getName() + " has fallen.")));
        if (run.members.isEmpty()) Bukkit.getScheduler().runTask(plugin, () -> endRun(run, null));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Location back = readReturn(p);
        if (back != null) {
            event.setRespawnLocation(back);
            p.getPersistentDataContainer().remove(Keys.RETURN_LOC);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        DungeonRun run = runByPlayer.remove(p.getUniqueId());
        if (run != null) {
            run.members.remove(p.getUniqueId());
            if (run.bossBar != null) p.hideBossBar(run.bossBar);
            if (run.members.isEmpty()) endRun(run, null);
        }
        invites.remove(p.getUniqueId());
    }

    /** A player who logged out (or the server stopped) mid-run is sent back on join. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (p.getPersistentDataContainer().has(Keys.RETURN_LOC, PersistentDataType.STRING) || p.getWorld().equals(world)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !inRun(p)) sendBack(p, false);
            }, 5L);
        }
    }

    // =================================================================== protection

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld().equals(world) && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            if (event.getBlock().getType() == Material.CHEST) return;
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlock().getWorld().equals(world) && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (event.getEntity().getWorld().equals(world)) event.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getBlock().getWorld().equals(world)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!event.getLocation().getWorld().equals(world)) return;
        CreatureSpawnEvent.SpawnReason r = event.getSpawnReason();
        if (r == CreatureSpawnEvent.SpawnReason.NATURAL || r == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
                || r == CreatureSpawnEvent.SpawnReason.PATROL) {
            event.setCancelled(true);
        }
    }

    public DungeonRun runOf(Player p) {
        return runByPlayer.get(p.getUniqueId());
    }

    public Iterator<DungeonRun> iterator() {
        return runs.values().iterator();
    }
}
