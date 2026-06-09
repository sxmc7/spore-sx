[根目录](../../../../../CLAUDE.md) > **Sentities (实体系统)**

# Sentities Module - 实体系统

## 模块职责

Sentities 模块包含 Spore Mod 中所有自定义实体的实现，涵盖从基础感染生物到灾厄级 Boss 的完整进化体系。实体架构分为 6 个主要阶层，支持多部分实体（Multipart）、投射物和实用实体。

## 实体阶层体系

```
BasicInfected (基础) -> EvolvedInfected (进化) -> Hyper (超) -> Calamities (灾厄)
                                                                     |
Organoids (器官类)   -> 独立分支
Experiments (实验体) -> 独立分支
```

### 各阶层详情

| 阶层 | 目录 | 描述 | 代表实体 |
|------|------|------|---------|
| BasicInfected | `BasicInfected/` | 基础感染生物，由原版生物感染而来 | InfectedHuman, InfectedHusk, InfectedVillager, InfectedDrowned, InfectedWitch, InfectedPillager, InfectedHazmat 等 |
| EvolvedInfected | `EvolvedInfected/` | 进化感染生物，更强的战斗能力 | Knight, Griefer, Braiomil, Leaper, Slasher, Spitter, Thorn, Howler, Stalker, Brute, Busser, Volatile, Chemist, Conductor, Bloater, Nuclea, Scamper, Jagdhund, Scavenger, Naiad, Protector, Gargoyl, Vanguard, Reaper |
| Hyper | `Hyper/` | 超感染生物，小型Boss级 | Wendigo, Inquisitor, Brotkatze, Hevoker, Ogre, Hvindicator, Grober, Mephetic |
| Calamities | `Calamities/` | 灾厄级生物，巨型Boss | Sieger, Gazenbreacher, Hindenburg, Howitzer, Hohlfresser, Grakensenker (Kraken), Stahlmorder, Leviathan |
| Organoids | `Organoids/` | 器官类生物，特殊机制 | Vigil, Usurper, Proto, HiveTumor, Reconstructor, Mound, Brauerei, Umarmer, Delusionare, Verwa, GastGeber, Specter |
| Experiments | `Experiments/` | 实验体 | Plagued, Lacerator, Biobloob, Saugling |

### 辅助实体

| 子模块 | 描述 |
|--------|------|
| `BaseEntities/` | 基础实体类（多部分实体 HohlMultipart, LeviathanMultipart 等） |
| `Projectile/` | 投射物实体，含 `GunProjectiles/` 子目录（AssassinBullet, BileBullet, GoreBullet） |
| `Utility/` | 辅助实体（ScentEntity, InfectionTendril, CorpseEntity, 各种投射物基类） |
| `FallenMultipart/` | 多部分实体的身体部件（HowitzerArm, Licker, SiegerTail, StalhArm） |
| `anticheat/` | SporeBossEntity 接口（用于 CoreMod 反作弊血量检测） |

## 入口与启动

- 所有实体通过 `Core/Sentities.java` 的 `DeferredRegister<EntityType<?>>` 注册
- 自定义 MobCategory: `INFECTED` (配置控制上限) 和 `ORGANOID` (上限20)

## 对外接口

- 继承 `net.minecraft.world.entity.Mob` 或 `net.minecraft.world.entity.PathfinderMob`
- 实现 `ArmorPersentageBypass` 等自定义接口
- Boss 实体实现 `SporeBossEntity` 接口（在 anticheat 包中）
- 投射物继承各类 `Projectile` 基类

## 关键依赖与配置

- `SConfig.SERVER.spawns`: 控制每种实体的生成权重、最小/最大数量
- `SConfig.SERVER.dimension_parameters`: 控制生成维度
- `SConfig.SERVER.dimension_blacklist`: 禁止生成的维度
- `SConfig.SERVER.mob_cap`: 自定义 INFECTED 种群上限

## 数据模型

- 实体类型: `DeferredRegister<EntityType<?>>` -> `Sentities.SPORE_ENTITIES`
- 实体通过 `EntityType.Builder.of()` 创建，指定尺寸和分类
- MobCategory: `INFECTED` (可配置cap), `ORGANOID` (cap=20)

## 测试与质量

- **无测试文件**
- 使用多部分实体（Multipart）系统支持巨型Boss的分部位伤害

## 相关文件清单

- `BaseEntities/` - 实体基类
- `BasicInfected/` - 基础感染生物 (10+ 类)
- `EvolvedInfected/` - 进化感染生物 (25+ 类)
- `Hyper/` - 超感染生物 (8 类)
- `Calamities/` - 灾厄级生物 (8 类)
- `Organoids/` - 器官类生物 (11 类)
- `Experiments/` - 实验体 (4 类)
- `Projectile/` - 投射物 (15+ 类，含子弹、投掷武器)
- `Utility/` - 实用实体 (触手、嗅探实体、尸体等)
- `FallenMultipart/` - 多部分实体部件
- `anticheat/` - 反作弊接口

## 常见问题

- **实体生成**: 通过 `BiomeModification.java` 和配置文件的 spawns 列表控制
- **多部分实体**: Sieger, Hohlfresser, Stahlmorder, Howitzer, Leviathan 等巨型Boss使用Multipart系统
- **投射物**: 每种枪械都有自己的子弹类型（AssassinBullet, BileBullet, GoreBullet）

## 变更记录

### 2026-05-04
- 创建模块级 CLAUDE.md
