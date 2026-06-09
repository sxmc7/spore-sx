[根目录](../../../../../CLAUDE.md) > **Sitems (物品系统)**

# Sitems Module - 物品系统

## 模块职责

Sitems 模块包含 Spore Mod 中所有自定义物品的实现，包括武器、盔甲、食物、原料、工具、注射器、唱片等。

## 子模块结构

| 子模块 | 路径 | 描述 |
|--------|------|------|
| 武器 | `Sitems/` | 20+ 种近战/远程武器 |
| 盔甲 | `Sitems/` | 4 套完整盔甲 + 马铠 + 鞘翅 |
| 枪械 | `Guns/` | 3 种生物枪械 (MistMaker, BileBlaster, AcidicAssassin) |
| 注射器 | `Sitems/` | 10+ 种注射器 (Syringe, EvolutionSyringe, WeaponSyringe 等) |
| 试剂 | `Agents/` | 6 种生物试剂 (Symbiotic, Cryogenic, Gastric, Corrosive 等) |
| 食物 | `Sitems/` | 30+ 种食物物品 |
| 唱片 | `Sitems/` | 3 张自定义唱片 + 大量音乐盘 |
| 基类 | `BaseWeapons/` | 武器和盔甲的基类 |

## 入口与启动

- 所有物品通过 `Core/Sitems.java` 的 `DeferredRegister<Item>` 注册
- 物品实例通过 `Sitems.ITEMS.register()` 创建

## 武器列表

| 武器 | 类 | 描述 |
|------|-----|------|
| Saber | InfectedSaber | 感染军刀 |
| Greatsword | InfectedGreatSword | 巨剑 |
| Cleaver | InfectedCleaver | 砍刀 |
| Armads | InfectedArmads | 臂刃 |
| Bow | InfectedGreatBow | 感染弓 |
| Maul | InfectedMaul | 大锤 |
| Scythe | InfectedScythe | 镰刀 |
| Spear | InfectedSpearItem | 长矛 |
| Crossbow | InfectedCrossbow | 十字弩 |
| Mace | InfectedMace | 钉头锤 |
| Sickle | InfectedSickle | 镰 (投掷) |
| Halberd | InfectedHalbert | 戟 |
| Knife | InfectedKnife | 匕首 (投掷) |
| Boomerang | InfectedBoomerang | 回旋镖 |
| Rapier | InfectedRapier | 细剑 |
| Shield | InfectedShield | 盾牌 |
| Combat Pickaxe | InfectedPickaxe | 战斗镐 |
| Combat Shovel | InfectedCombatShovel | 战斗锹 |
| Reaver | Reaver | 收割者 |
| PCI | PCI | PCI 注射枪 |
| Syringe Gun | SyringeGun | 注射枪 |

### 枪械 (Guns/)

| 枪械 | 描述 |
|------|------|
| MistMaker | 迷雾制造者 |
| BileBlaster | 胆汁冲击枪 |
| AcidicAssassin | 酸性刺客 |

## 盔甲系统

| 套装 | 等级 | 类前缀 |
|------|------|--------|
| Infected | 基础 | InfectedHelmet/Chestplate/Leggings/Boots |
| Plated | 进阶 | PlatedHelmet/Chestplate/Leggings/Boots |
| Living | 高级 | LivingHelmet/Chestplate/Leggings/Boots |
| Infected Upgraded | 升级版 | InfectedUpHelmet/Chestplate/Pants/Boots |

**特殊**: R_Elytron (感染鞘翅), 3种马铠 (Flesh/Plated/Living 马铠)

## 注射器与变异

| 注射器 | 目标 | 效果 |
|--------|------|------|
| Syringe | 基础 | 空注射器 |
| MutationSyringe | 通用 | 变异注射器 |
| EvolutionSyringe | 通用 | 进化注射器 |
| VampiricSyringe | 武器 | 吸血变异 |
| CalcifiedSyringe | 武器 | 钙化变异 |
| BezerkSyringe | 武器 | 狂战变异 |
| ToxicSyringe | 武器 | 毒液变异 |
| RottenSyringe | 武器 | 腐烂变异 |
| ReinforcedSyringe | 盔甲 | 强化变异 |
| SkeletalSyringe | 盔甲 | 骨骼变异 |
| DrownedSyringe | 盔甲 | 溺亡变异 |
| CharredSyringe | 盔甲 | 焦炭变异 |

## 生物试剂 (Agents/)

| 试剂 | 附魔 | 适用 |
|------|------|------|
| Symbiotic Reagent | Symbiotic Reconstitution | 所有 |
| Cryogenic Reagent | Cryogenic Aspect | 武器 |
| Gastric Reagent | Gastric Spewage | 武器 |
| Corrosive Reagent | Corrosive Potency | 武器 |
| Serrated Reagent | Serrated Thorns | 盔甲 |
| Voracious Reagent | Voracious Maw | 所有 |

## 刷怪蛋

40+ 种刷怪蛋，涵盖所有实体类型，通过 `SporeSpawnEgg` 类创建，使用 `SpawnEggType` 枚举分类:
- `INFECTED` - 基础感染
- `EVOLVED` - 进化型
- `HYPER` - 超感染
- `CALAMITY` - 灾厄级
- `EXPERIMENT` - 实验体
- `ORGANOID` - 器官类
- `UNKNOWN` - 未知

## 测试与质量

- **无测试文件**

## 相关文件清单

- `BaseWeapons/` - 武器和盔甲基类
- `Guns/` - 枪械实现
- `Agents/` - 试剂实现
- `Sitems.java` (in Core/) - 物品注册

## 变更记录

### 2026-05-04
- 创建模块级 CLAUDE.md
