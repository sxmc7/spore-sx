# Spore

*A Minecraft Forge mod about fungal infection and biological experiments.*

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-success)
![Forge](https://img.shields.io/badge/Forge-47.1.0-blue)
![Java](https://img.shields.io/badge/Java-17-orange)

---

## Overview

Spore is a Minecraft mod that introduces a **fungal infection dimension** to your world. Explore a world overrun by bizarre fungal creatures, harvest their biomass to craft powerful weapons and armor, and uncover the truth behind the outbreak.

Progress through **six tiers of enemies** — from basic infected villagers to world-ending calamity bosses — each with unique AI, attack patterns, and loot.

---

## Features

### Entity System — 6 Tiers

| Tier | Examples | Description |
|------|----------|-------------|
| **Basic Infected** | Infected Human, Husk, Villager, Drowned, Witch, Pillager, Hazmat | Common infected mobs, variants of vanilla mobs |
| **Evolved Infected** | Knight, Griefer, Leaper, Slasher, Howler, Brute, Bloater, Stalker, Chemist, Conductor, Scavenger, Reaper, Protector, Vanguard, Jagdhund, Naiad, Gargoyl, Busser, Volatile, Braiomil, Scamper, Thorn, Spitter, Nuclea | Specialized combat forms with distinct abilities |
| **Hyper** | Wendigo, Inquisitor, Brotkatze, Hevoker, Ogre, Hvindicator, Grober, Mephetic | Mini-boss tier, formidable encounters |
| **Calamities** | Sieger, Gazenbreacher, Hindenburg, Howitzer, Hohlfresser, Grakensenker (Kraken), Stahlmorder, Leviathan | Giant multi-part bosses with complex mechanics |
| **Organoids** | Vigil, Usurper, Proto, HiveTumor, Reconstructor, Mound, Brauerei, Umarmer, Delusionare, Verwa, GastGeber, Specter | Special-mechanism entities with unique roles |
| **Experiments** | Plagued, Lacerator, Biobloob, Saugling | Experimental lab creations |

### Weapons & Armor

- **15+ weapons**: swords, bows, crossbows, cleavers, guns, and more
- **Spore Armor set**: full-set bonuses including damage reduction, health regen, status immunity, and knockback resistance
- **Weapon/Armor mutation system**: inject syringes to mutate gear with special effects (vampirism, calcification, berserk, venom, decay)

### Machines & Crafting

- **CDU** — Central Processing Unit for entity transformation
- **Surgery Table** — Biological modification
- **Incubator** — Entity incubation
- **Zoaholic** — Item analysis
- **Custom recipes**: Surgery, Grafting, Injection, Assimilation

### Potions & Effects

- 6 custom status effects: Mycelium, Madness, Starvation, and more
- Custom potion types

### Technical

- **CoreMod ASM** — Runtime bytecode transformation for anti-cheat health validation
- **Multipart Entity System** — Support for giant multi-part bosses
- **JEI Integration** — Recipe display support

---

## Installation

1. Install **Minecraft Forge 47.1.0** for Minecraft 1.20.1
2. Download the latest `spore-sx.jar` from [Releases](https://github.com/YOUR_USERNAME/YOUR_REPO/releases)
3. Place the jar in your `mods/` folder
4. Launch the game

---

## Building from Source

### Prerequisites

- **Java 17** (JDK)
- Internet connection (Gradle downloads dependencies automatically)

### Build

```bash
git clone https://github.com/YOUR_USERNAME/YOUR_REPO.git
cd Spore
./gradlew build
```

The built jar will be at `build/libs/spore-*.jar`.

### IDE Setup

```bash
# IntelliJ IDEA
./gradlew idea

# Eclipse
./gradlew eclipse
```

### Run Client / Server

```bash
./gradlew runClient    # Launch game client
./gradlew runServer    # Launch dedicated server
```

---

## Configuration

Server configuration file: `run/sporeconfig.toml`

Key options:
- `enable_damage_limit` — Toggle damage limiter system
- `enable_percentage_damage` — Toggle percentage-based damage
- Mob spawning rates, dimensions, caps per entity type

---

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

```

## Credits

- **Harbinger** — Author and developer
- Minecraft Forge team — Modding framework
- [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) — Recipe integration
```
