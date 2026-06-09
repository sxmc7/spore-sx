[根目录](../../../../../CLAUDE.md) > **Core (注册中心)**

# Core Module - 注册中心

## 模块职责

Core 模块是 Spore Mod 的注册中心，包含所有 `DeferredRegister` 声明和初始化入口。此模块负责将方块、物品、实体、特效、声音、粒子、配方、菜单、流体、特征、画作、方块实体、属性和创造模式标签页注册到 Forge 注册系统。

## 入口与启动

- **初始化位置**: `Spore.java` (根模块) 的构造函数中依次调用各 register() 方法
- **注册方式**: 所有注册通过 `DeferredRegister` 实现惰性注册

## 注册表清单

| 注册表类 | 注册类型 | 注册对象数量 |
|---------|---------|------------|
| Sblocks.java | Block | 70+ 方块 (实验室方块、真菌植被、感染方块、机械方块等) |
| Sitems.java | Item | 200+ 物品 (武器、盔甲、食物、原料、工具、刷怪蛋等) |
| Sentities.java | EntityType | 70+ 实体类型 (含投射物、多部分实体) |
| Seffects.java | MobEffect | 6 个自定义效果 (Mycelium, Madness, Starvation 等) |
| Ssounds.java | SoundEvent | 150+ 音效事件 |
| Sparticles.java | ParticleType | 11 个粒子类型 |
| Srecipes.java | RecipeSerializer | 4 个自定义配方序列化器 |
| Sfluids.java | FluidType / Fluid | 1 种流体 (Bile) |
| Sfeatures.java | Feature | 特征注册 (占位) |
| ScreativeTab.java | CreativeModeTab | 2 个创造模式标签页 (生物/科技) |
| SMenu.java | MenuType | 12 个菜单类型 |
| Spaintings.java | PaintingVariant | 1 个画作变体 |
| SblockEntities.java | BlockEntityType | 10 个方块实体类型 |
| SAttributes.java | Attribute | 7 个自定义属性 |
| Spotion.java | Potion | 4 个药水类型 |
| SticketType.java | TicketType | 1 个区块加载票据 |
| Senchantments.java | Enchantment | 9 个自定义附魔 |

## 关键依赖与配置

- `SConfig.java`: 服务器配置 (`sporeconfig.toml`) 和数据生成配置 (`sporedata.toml`)
- 所有注册表类被主类 `Spore.java` 直接调用

## 数据模型

- 方块: `DeferredRegister<Block>` -> `Sblocks.BLOCKS`
- 物品: `DeferredRegister<Item>` -> `Sitems.ITEMS`
- 实体: `DeferredRegister<EntityType<?>>` -> `Sentities.SPORE_ENTITIES`
- 效果: `DeferredRegister<MobEffect>` -> `Seffects.MOB_EFFECTS`
- 所有注册通过 `RegistryObject` 获取实例

## 测试与质量

- **无测试文件**

## 相关文件清单

- `Sblocks.java` - 方块注册
- `Sitems.java` - 物品注册
- `Sentities.java` - 实体注册
- `Seffects.java` - 效果注册
- `Ssounds.java` - 声音注册
- `Sparticles.java` - 粒子注册
- `Srecipes.java` - 配方注册
- `Sfluids.java` - 流体注册
- `Sfeatures.java` - 特征注册
- `ScreativeTab.java` - 创造标签注册
- `SMenu.java` - 菜单注册
- `Spaintings.java` - 画作注册
- `SblockEntities.java` - 方块实体注册
- `SAttributes.java` - 属性注册
- `Spotion.java` - 药水注册
- `SticketType.java` - 区块加载票证
- `Senchantments.java` - 附魔注册
- `SConfig.java` - 配置管理

## 变更记录

### 2026-05-04
- 创建模块级 CLAUDE.md
