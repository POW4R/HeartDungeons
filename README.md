# HeartDungeons

A Lifesteal + EliteMobs style plugin for **Purpur / Paper 26.2+**. No client mods needed.

- Kill hostile mobs anywhere to earn XP and level up.
- **Every 5 levels = +1 max heart** (up to 20 hearts).
- **Withdraw hearts** as items (`/withdraw`) and give them to other players. Right-click a Heart to use it.
- You **don't lose hearts when you die**, except **inside dungeons**.
- **Generated dungeon runs** with elite mobs, a boss with abilities, and loot.
- **5 OP weapons** that drop from dungeon bosses.

## Build & install

Same as SiftDimension: needs Java 25.

```powershell
.\gradlew build
```

Copy `build\libs\HeartDungeons-1.0.0.jar` into `plugins\`, then restart the server.

## Dungeons

`/dungeon start [tier]` builds a fresh dungeon and teleports you (and your party) in.

- **3 themes:** Forgotten Crypt (undead, boss: The Hollow King), Infernal Keep (nether mobs, boss: Warlord Gorrak),
  Sunken Temple (drowned and illagers, boss: The Tidecaller).
- **Layout:** a start room, 5 combat rooms (configurable), then a boss room.
  Entering a room spawns elites. The door ahead opens once they're all dead.
- **Elites** get more health, damage and better armour with each tier, and the party gets more as it grows.
  Some get affixes: Swift, Armored, Brutal or Undying.
- **The boss** has a health bar and three abilities:
  - **Shockwave:** knocks nearby players back.
  - **Summons:** calls in minions.
  - **Fireballs:** at tier 3+, and always in the Infernal Keep.
  - **Enrage:** at half health it gets faster and stronger.
- **Rewards:** XP for everyone, plus a loot chest with diamonds, emeralds, golden apples and XP bottles.
  At higher tiers it can also hold netherite scrap, a **Heart**, or an **OP weapon**.
  Cleared rooms sometimes leave a supply chest.
- **Dying in a dungeon:** you lose 1 heart (never below 3), keep your items, and respawn where you entered.
- **Tiers 1-5** unlock at levels 0 / 10 / 20 / 30 / 45 (set in config).

Dungeons are built in a separate world, `heartdungeons_instances`. It's wiped on every server start, so it never
grows. Players can't break or place blocks there.

### Parties (max 5)

`/dungeon party invite <player>`, `/dungeon party accept`, `/dungeon party leave`, `/dungeon party list`.
The leader runs `/dungeon start`. Everyone online in the party comes along.

## Weapons

| Weapon | Base | Ability |
|---|---|---|
| Vampire Blade | Netherite sword (10 dmg) | Heals you 25% of damage dealt |
| Stormbreaker | Mace | Right-click: lightning where you look, 8 damage in an area (10 s cooldown) |
| Reaper's Scythe | Netherite hoe (11 dmg) | Hits sweep all nearby mobs for half damage and Wither them |
| Inferno Bow | Bow (Power IV) | Arrows explode into fire (no block damage) |
| Frostbite Trident | Trident (Loyalty III) | Freezes and heavily slows the target |

## Commands

| Command | |
|---|---|
| `/stats [player]` | Level, XP, hearts, level of next heart |
| `/withdraw [amount]` | Turn hearts into Heart items (you keep at least 3) |
| `/dungeon start [tier]` · `leave` · `tiers` · `party …` | Dungeons |
| `/dungeon admin weapon <type> [player]` | Give a weapon (op) |
| `/dungeon admin heart [amount] [player]` | Give Heart items (op) |
| `/dungeon admin setlevel <player> <level>` · `sethearts <player> <n>` | Edit players (op) |
| `/dungeon admin endall` | Close every running dungeon (op) |

## Config (`plugins/HeartDungeons/config.yml`)

Starting/min/max hearts, levels per heart, hearts lost on dungeon death, XP curve, max level,
level in tab list, number of rooms, time limit, exit delay, tier unlock levels.

## Good to know

- Player level, XP and hearts are saved on the player, so they survive restarts.
- If the server stops or a player logs out mid-run, they're sent back where they entered.
- The Heart item is a Nether Star underneath, but it can't be used in crafting (no beacons from Hearts).
- XP from mobs is shown in the action bar. Level-ups show a title and give hearts at every 5th level.
