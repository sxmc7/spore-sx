[根目录](../../../../../CLAUDE.md) > **Sblocks (方块系统)**

# Sblocks Module - 方块系统

## 模块职责

Sblocks 模块包含所有自定义方块的实现，分为机械方块、实验室装饰方块、真菌植被、感染方块和生物质方块几大类。

## 方块分类

### 实验室方块 (Laboratory)
| 方块 | 类 | 描述 |
|------|-----|------|
| Lab Block (1-3) | Block | 实验室墙面 |
| Lab Slab (1-3) | SlabBlock | 实验室楼梯 |
| Lab Stair (1-3) | StairBlock | 实验室台阶 |
| Iron Ladder | IronLadderBlock | 铁梯子 |
| Vent Plate | VentPlateBlock | 通风口 |
| Rusted Vent Plate | VentPlateBlock | 锈蚀通风口 |
| Vent Door | TrapDoorBlock | 通风口活板门 |
| Laboratory Bed | LabBedBlock | 实验室床 |

### 机械方块 (Machines)
| 方块 | 类 | 描述 |
|------|-----|------|
| Container | Container | 容器 |
| CDU | CDUBlock | CDU 处理单元 |
| Zoaholic | ZoaholicBlock | Zoaholic 分析仪 |
| Incubator | IncubatorBlock | 孵化器 |
| Surgery Table | SurgeryTableBlock | 手术台 |
| Cabinet | Cabinet | 柜子 |
| Organite | OrganiteBlock | 器官化仪 |

### 安全方块 (Security)
| 方块 | 类 | 描述 |
|------|-----|------|
| Reinforced Door | DoorBlock | 强化门 (3种变体: 普通/锈蚀/冰冻) |

### 真菌植被 (Fungal Flora)
| 方块 | 描述 |
|------|------|
| Growths Big/Small | 真菌生长物 |
| Fungal Stem Sapling | 真菌茎苗 |
| Fungal Stem / Top | 真菌茎 |
| Underwater Fungal Stem | 水下真菌茎 |
| Hanging Fungal Stem | 悬挂真菌茎 |
| Fungal Roots | 真菌根 |
| Bloom G / Bloom GG | 开花真菌 |
| Glowshroom | 发光蘑菇 |
| Acidic Sack | 酸性囊泡 |
| Growth Mycelium | 生长菌丝体 |
| Wall Growths (Big/Fleshy) | 墙面生长物 |
| Mycelium Veins | 菌丝静脉 |
| Biomass Bulb | 生物质球茎 |
| Vocals | 发声器官方块 |

### 感染方块 (Infested)
| 方块 | 描述 |
|------|------|
| Infested Dirt/Stone/Netherrack/SoulSand/EndStone/Sand/Gravel/Deepslate/RedSand/Clay/Cobblestone/CobbledDeepslate/StoneBricks/Bricks | 感染变种方块 |
| Infested Laboratory Block (1-3) | 感染实验室方块 |

### 生物质方块 (Biomass)
| 方块 | 类 | 描述 |
|------|-----|------|
| Biomass Lump | BiomassLump | 生物质块 |
| Rooted Biomass | Block | 根化生物质 |
| Biomass Block | Block | 生物质方块 |
| Sicken Biomass Block | SickenBiomassBlock | 病变生物质 |
| Gastric Biomass | GastricBiomassBlock | 胃化生物质 |
| Calcified Biomass Block | Block | 钙化生物质 |
| Membrane Block | MembraneBlock | 膜块 |
| Rooted Mycelium | Block | 根化菌丝体 |
| Fungal Shell | Block | 真菌外壳 |
| Mycelium Block/Slab | RotatedPillarBlock/SlabBlock | 菌丝方块 |
| Frost Burned Biomass | FrozenBiomass | 霜冻生物质 |
| Bile | BileLiquidBlock | 胆汁液体 |
| Crusted Bile | CrustedBile | 结痂胆汁 |

### 其他方块
| 方块 | 描述 |
|------|------|
| Hand | 手 |
| Lungs (CancerLungs) | 癌变肺 |
| Overgrown Spawner | 丛生刷怪笼 |
| Brain Remnants | 脑残留 |
| Outpost Watcher | 前哨观察者 |
| Skull Soup | 头颅汤 (食物) |
| Heart Pie | 心脏派 (食物) |
| Cooked Torso | 烤躯干 (食物) |
| Rotten Log/Planks/Stair/Slab/Scraps/Branch/Bush/Grass/Fern/Crops | 腐烂木系列 |
| Hive Spawn | 蜂巢生成器 |
| Remains/Wall Remains/Frozen Remains | 残骸系列 |
| Cerebrum/Innards/Heart/Braio Block | 器官方块 |
| Drowned Lump/Bile Lump/Fang Lump | 掉落物方块 |
| Acid | 酸液 |

## 入口与启动

- 所有方块通过 `Core/Sblocks.java` 的 `DeferredRegister<Block>` 注册
- 方块物品通过 `Core/Sitems.java` 注册

## 测试与质量

- **无测试文件**

## 相关文件清单

- `Sblocks.java` (in Core/) - 方块注册
- `Sitems.java` (in Core/) - 方块物品注册

## 变更记录

### 2026-05-04
- 创建模块级 CLAUDE.md
