[根目录](../../../../../CLAUDE.md) > **sEvents (事件处理)**

# sEvents Module - 事件处理

## 模块职责

sEvents 模块处理所有 Forge 事件总线事件，包括实体生成、玩家交互、伤害计算、方块交互、世界加载等。

## 入口与启动

- `HandlerEvents.java` 是主事件处理类
- 通过 `MinecraftForge.EVENT_BUS.register(this)` 在主类 `Spore.java` 中注册
- 通过 `modEventBus.addListener()` 注册模组生命周期事件

## 主要功能

- **生成放置**: `SpawnPlacement` - 控制感染生物的生成规则
- **各种 Forge 事件**: 实体交互、伤害、存活检测等
- **命令注册**: 调试/管理命令
- **方块实体 Tick**: LivingStructureBlocks 等自定义 tick

## 关键依赖与配置

- 依赖所有 Core 注册表
- 使用 `SConfig.SERVER` 中的配置值

## 测试与质量

- **无测试文件**

## 相关文件清单

- `HandlerEvents.java` - 主事件处理类

## 变更记录

### 2026-05-04
- 创建模块级 CLAUDE.md
