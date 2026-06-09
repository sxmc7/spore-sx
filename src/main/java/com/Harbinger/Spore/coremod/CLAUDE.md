[根目录](../../../../../CLAUDE.md) > **coremod (字节码变换系统)**

# CoreMod Module - 运行时字节码变换

## 模块职责

此模块实现 CoreMod 功能，通过 Java Instrumentation API 和 ASM 库在运行时修改 Minecraft 字节码。这是模组的反作弊和反篡改机制的核心。

## 入口与启动

- **主入口**: `CoreModMain.java`
- **初始化位置**: `Spore.java` 构造函数末尾调用 `CoreModMain.loadAgentAndTransformClasses()`
- **工作流程**:
  1. 通过 `VirtualMachine.attach()` 向自身 JVM 注入 agent
  2. 使用 `Instrumentation.retransformClasses()` 修改已加载的类

## 功能列表

### 1. Ticking & Rendering 变换
- 修改 `net.minecraft.client.renderer.LevelRenderer` - 自定义渲染
- 修改 `net.minecraft.server.MinecraftServer` - 服务器行为
- 通过 `SporeClassVisitor` (ASM ClassVisitor) 实现

### 2. GetHealth 方法变换 (所有 LivingEntity)
- 对所有非 Spore 模组的 `LivingEntity` 实现类，修改 `getHealth()` 方法
- 通过 `GetHealthPatcherClassVisitor` 实现

### 3. 自身 Boss 实体变换
- 查找所有实现 `SporeBossEntity` 接口的类
- 修改它们的 `getHealth()` 方法用于血量同步

### 4. Mixin 防护
- 使用 `PreventMixinsClassVisitor` 修改 `EntityUtil` 类
- 防止其他模组通过 Mixin 修改关键逻辑

### 5. CoreModHooks — 运行时钩子 (coremod/CoreModHooks.java)

核心运行时拦截逻辑，通过 ASM 注入到原版方法中，不依赖 Agent 类变换即可工作。

| 钩子 | 注入目标 | 职责 |
|------|----------|------|
| `limitSetHealth()` | LivingEntity.setHealth() | 字节码级限伤 + 血量影子备份 |
| `checkRealInvuln()` | LivingEntity.hurt() 入口 | NBT 真实无敌帧阻挡 |
| `onHurtReturn()` | LivingEntity.hurt() 返回 | 命中后设置无敌帧 NBT |
| `shouldBlockHeal()` | LivingHealEvent | 禁疗标记拦截 |
| `clearRealInvuln()` | 外部调用 | 清除无敌帧（Spore 武器绕过） |
| `applyTrueDamage()` | 外部调用 | 委托 UnsafeHealthHelper 直写真伤 |
| `tickServer()` | 每 tick | 限伤系统 + 血量校验 + Bleed |
| `isTimeStopped()` | 外部调用 | 时停检查 |
| `checkDespawnImmunity()` | 外部调用 | 反生成免疫 |

#### limitSetHealth() 处理流程

```
setHealth(newHealth)
  → Layer 0: HealthFieldUtil.isAuthorized()? → 放行
  → Layer 1: 真实无敌帧? → 返回 currentHealth
  → Layer 2: 非 Spore 或血量增加? → 放行
  → Layer 3: 单次最多扣 max(current * 1%, 0.5f)
  → Layer 4: DamageLimiter.applyFrameCap() 窗口限伤
  → recordHealthBackup()
  → return newHealth
```

#### 真实无敌帧系统

- 硬闸在 hurt() 最开头，NBT `spore_real_invuln` 存储到期 tick
- 持续时间：4 tick（0.2 秒）
- 每次有效 hit 刷新
- Spore 武器/生物通过 `clearRealInvuln()` 绕过

#### 血量影子备份

- `recordHealthBackup()` — 在 limitSetHealth 扣血通过时记录
- `checkHealthConsistency()` — 每 tick 校验，偏差 > 0.5f 自动恢复
- 影子备份 200 tick (10s) 过期自动清理

## 关键技术

- **ASM 9.5**: `ClassReader`, `ClassVisitor`, `MixinClassWriter`, ASM Tree API (`InsnList`, `MethodNode` 等)
- **Java Instrumentation API**: `ClassFileTransformer`, `Instrumentation.retransformClasses()`
- **Attach API**: `VirtualMachine.attach()` -> `loadAgent()` -> `detach()`
- **Unsafe**: 通过 `UnsafeUtil` 获取 `Unsafe` 实例设置 `ALLOW_ATTACH_SELF`

## Agent 加载方式

- 将嵌入在 JAR 中的 `spore_agent.jar` 提取到临时文件
- 通过 Attach API 将 agent 注入自身 JVM
- Agent 设置 `CoreModMain.instrumentation` 供后续使用

## 跟踪状态

`CoreModMain.SUCCESSFUL_OPERATIONS` HashMap 记录每个操作的成功状态:
- `allow` - ALLOW_ATTACH_SELF 设置
- `file` - agent jar 临时文件创建
- `agent_attach` - agent 注入
- `agentmain` - agent 回调
- `metapotent_flashfur` - 渲染/服务器变换

## 测试与质量

- **无测试文件**
- 容错设计: 如果 agent 加载失败或类变换失败，模组会记录警告但不崩溃

## 相关文件清单

- `CoreModMain.java` - 主入口，控制 agent 加载和类变换
- `SporeClassVisitor.java` - ASM ClassVisitor (LevelRenderer, MinecraftServer)
- `GetHealthPatcherClassVisitor.java` - ASM ClassVisitor (血量方法)
- `PreventMixinsClassVisitor.java` - ASM ClassVisitor (Mixin 防护)

## 常见问题

- **Agent 不工作**: 在某些 JVM 上 Attach API 可能不可用，模组会继续运行但无字节码修改
- **兼容性问题**: 其他模组可能干扰类变换，模组设计为容错运行

## 变更记录

### 2026-05-04
- 创建模块级 CLAUDE.md
