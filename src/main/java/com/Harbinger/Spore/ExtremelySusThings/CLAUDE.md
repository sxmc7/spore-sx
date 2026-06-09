[根目录](../../../../../CLAUDE.md) > **ExtremelySusThings (网络与世界生成)**

# ExtremelySusThings Module - 网络通信与世界生成

## 模块职责

此模块处理网络通信、生物群系/结构修改、自定义 JSON 数据加载和实用工具函数。

## 子模块结构

| 子模块 | 描述 |
|--------|------|
| `Package/` | 网络数据包定义 (9 种) |
| `CustomJsonReader/` | 自定义 JSON 数据加载器 (SporeConversionData, SporeMobConversionData, SporeCduConversionData 等) |

## 网络通信

- **通道**: SimpleChannel 名为 `spore:main`，版本 `1`
- **数据包列表**:
  - `RequestAdvancementPacket` - 请求进度同步
  - `SyncAdvancementPacket` - 同步进度
  - `AdvancementGivingPackage` - 给予进度
  - `SporeGunFirePacket` - 枪械开火
  - `SporeGunFireSyncPacket` - 枪械开火同步
  - `OpenGraftingScreenPacket` - 打开嫁接界面
  - `OpenSurgeryScreenPacket` - 打开手术界面
  - `SongInitializingPacket` - 音乐初始化

## 世界生成修改

- **BiomeModification**: 根据配置文件中的 `spawns` 列表和 `dimension_parameters`，在生物群系中添加感染生物生成
  - 蘑菇岛 biome 有 +20 权重加成
  - 黑名单机制 (`dimension_blacklist`)
- **StructureModification**: 在结构中添加感染生物生成

## JSON 数据加载

- `CustomJsonReader` 包中包含自定义 JSON 数据加载器，用于从数据包中读取配置：
  - `SporeConversionData` / `SporeConversionReloadListener` - 方块转换数据
  - `SporeMobConversionData` / `SporeMobConversionReloadListener` - 生物转换数据
  - `SporeCduConversionData` - CDU 转换数据

## 工具类

- `Utilities.java` - 通用工具函数

## 配置

- 生成配置: `SConfig.SERVER.spawns`, `SConfig.SERVER.dimension_parameters`, `SConfig.SERVER.dimension_blacklist`

## 测试与质量

- **无测试文件**

## 相关文件清单

- `SporePacketHandler.java` - 网络包处理器
- `BiomeModification.java` - 生物群系修改
- `StructureModification.java` - 结构修改
- `Package/` - 网络数据包定义
- `CustomJsonReader/` - JSON 数据加载器
- `Utilities.java` - 工具函数

## 变更记录

### 2026-05-04
- 创建模块级 CLAUDE.md
