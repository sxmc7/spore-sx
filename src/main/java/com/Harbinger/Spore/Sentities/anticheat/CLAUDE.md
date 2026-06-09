# AntiCheat Module — 防御机制与改血绕过系统

[根目录](../../../../../../../CLAUDE.md) > [Sentities](../CLAUDE.md) > **anticheat (防御机制)**

## 模块职责

Spore 模组的防御机制分为两大子系统：

1. **Incoming Defense（受击防御）** — 保护 Spore 实体免受外部伤害滥用
2. **Outgoing Bypass（攻击绕过）** — Spore 攻击者突破目标伤害限制

## 一、防御分层架构

```
外部攻击/伤害源
    │
    ├─ ① CoreMod ASM 字节码注入层
    │   ├─ limitSetHealth() — LivingEntity.setHealth() 拦截
    │   ├─ checkRealInvuln() — hurt() 开头真实无敌帧
    │   └─ shouldBlockHeal() — 禁疗拦截
    │
    ├─ ② DamageLimiter 滑动窗口层
    │   ├─ hurt 路径限伤（WINDOW_CAP_HURT = 15/tick）
    │   ├─ setHealth 路径限伤（WINDOW_CAP_SETHEALTH = 10/tick）
    │   └─ 自适应无敌帧（BASE=10, MAX=40 ticks）
    │
    ├─ ③ 实体 setHealth() 覆写层
    │   ├─ Infected.setHealth() / UtilityEntity.setHealth()
    │   ├─ IN_HURT_CHAIN + HealthFieldUtil.isAuthorized() 白名单
    │   └─ 1% 单次最大扣血限制
    │
    ├─ ④ SporeHealthStorage 编码存储层
    │   ├─ 8槽 XOR-rotate 编码
    │   └─ WeakHashMap 自动 GC
    │
    └─ ⑤ HealthManager 血量异常恢复层
        ├─ 血量异常检测（<0.1f 但未死亡）
        └─ 恢复到上次健康血量
```

## 二、Incoming Defense 入站防御

### 2.1 CoreModHooks (coremod/)

| 方法 | 作用 | 触发点 |
|------|------|--------|
| `limitSetHealth()` | ASM 注入 LivingEntity.setHealth() 入口处 | 所有 setHealth 调用 |
| `checkRealInvuln()` | hurt() 最开头检查 NBT 无敌帧 | ASM 注入 hurt() 入口 |
| `onHurtReturn()` | hurt() 返回时设置 NBT 无敌帧 | ASM 注入 hurt() 返回 |
| `shouldBlockHeal()` | LivingHealEvent 中检查禁疗标记 | Forge 事件 |
| `clearRealInvuln()` | 清除无敌帧（Spore 武器绕过用） | 对外调用 |

**limitSetHealth() 处理流程：**
1. Layer 0: `HealthFieldUtil.isAuthorized()` → 无条件放行（AUTHORIZED ThreadLocal）
2. Layer 1: 真实无敌帧 → 阻挡扣血
3. Layer 2: 非 Spore 实体直接放行；Spore 实体 → 进入下一步
4. Layer 3: 单次最多扣 `max(current * 1%, 0.5f)`
5. Layer 4: `DamageLimiter.applyFrameCap()` setHealth 窗口限伤

### 2.2 DamageLimiter 滑动窗口系统

| 参数 | 值 | 说明 |
|------|-----|------|
| WINDOW_SIZE | 20 tick | 1秒窗口 |
| WINDOW_CAP_HURT | 15.0 | hurt 路径每秒上限 |
| WINDOW_CAP_SETHEALTH | 10.0 | setHealth 路径每秒上限 |
| BASE_INVULN_TICKS | 10 | 基础无敌帧 (0.5s) |
| MAX_INVULN_TICKS | 40 | 最大无敌帧 (2s) |
| CONSECUTIVE_WINDOW | 5 | 连续判定窗口 |

**Spore 护甲双模式：** 非 Spore 攻击者攻击穿着 Spore 护甲的实体时，额外限伤至 `max(1, maxHealth * 1%)`。

**Spore 武器白名单：** 持有 `SporeWeaponData` 武器的攻击者跳过无敌帧和窗口限伤（但仅对普通伤害，真伤走独立路径）。

### 2.3 实体 setHealth() 覆写

`Infected` 和 `UtilityEntity` 均覆写 `setHealth()`：
- `SporeHealthStorage.getHealth()` 返回 0 时回退到 `super.getHealth()`
- `IN_HURT_CHAIN` ThreadLocal 防止递归
- `HealthFieldUtil.isAuthorized()` 标记绕过
- `DamageLimiter.isBypassInvulnerable()` 检查 bypass 无敌帧
- 单次限伤 `max(current * 1%, 0.5f)` + `DamageLimiter.applyFrameCap()`

### 2.4 SporeHealthStorage 编码存储

```
8 槽 long[] + 随机 activeIndex + 随机 key
编码：float²int → XOR key → Integer.rotateRight(7)
```

- WeakHashMap 自动 GC，无需手动注册/注销
- 未注册实体 `getHealth()` 返回 0f（调用方已做 fallback）
- 每次写操作：随机换 1 槽 + 额外扰动 3 槽

### 2.5 辅助系统

| 类 | 职责 |
|----|------|
| `AccessChecker` | StackChecker + 特权操作验证 + 高频缓存 (100ms) |
| `StackChecker` | StackWalker 堆栈检查，超时保护 (30s 锁定) |
| `ProtectedWeakHashMap` | 带 AccessChecker 保护的 WeakHashMap |
| `HealthManager` | 血量异常检测 + 恢复到健康血量 |
| `HealthRegainSystem` | 每帧血量监控 + 异常回滚 + 自动回血 |
| `SporeBossDefenseSystem` | Calamity 专用：伤害减免 + 阶段防御 + 独立血量 |
| `SporeEntityRegistry` | 实体加入世界时自动注册到限伤系统 |
| `SporeBossEntity` | Boss 实体接口（对标 omnimobs） |
| `SporeBossAttackSystem` | Boss 攻击系统（多种攻击类型 + 屏障） |
| `SporeBossAOESystem` | Boss AOE 系统（持续范围伤害 + 真伤） |

## 三、Outgoing Bypass 出站绕过

Spore 的攻击者（生物 + 武器 + AOE）需要突破目标防御，通过以下系统实现：

### 3.1 UnsafeHealthHelper — 4 层直写工具

```
攻击链（逐层增强）：
  1) Capability 实体 → cap.setCustomHealth()
  2) Unsafe 直写 DataItem.value（绕过 SynchedEntityData.set）
  3) HealthFieldUtil 补充写入（accessor/VarHandle/setHealth + AUTHORIZED）
  4) Delta 探测自定义字段 → Unsafe 直写（Mixin 覆写 getHealth 时的保底）
     ├─ 命中则缓存偏移
     └─ 未命中 → tryExternalHealthWrite() 反射调用外部存储
```

| 方法 | 作用 |
|------|------|
| `setHealth()` | 4 层攻击链直写，绕过所有防御 |
| `addHealth()` | 加减血量（负值扣血） |
| `getHealth()` | Capability → HealthFieldUtil 读血量 |
| `kill()` | 标准击杀 |
| `killByBruteForce()` | 零化所有 float/int 字段 (≤1000) |
| `killByRemovalFlag()` | Unsafe 设置 Entity.removed |
| `ultimateKill()` | 5 阶段终极击杀（kill → brute → boolean → removed） |
| `damagePercentage()` | 按百分比扣血 |

### 3.2 HealthFieldUtil — 3 层工具

```
HP 读取：accessor → VarHandle → entity.getHealth()
HP 写入：accessor → VarHandle → entity.setHealth()
```

- `AUTHORIZED` ThreadLocal：绕过 CoreModHooks.limitSetHealth 限伤
- `setHealth()` 对 Spore 目标自动同步 `SporeHealthStorage`
- `getHealth()` 对 Capability 实体路由到 Mixin
- `applyBleed()` / `tickBleed()`：持续伤害系统（每 tick 扣血，叠加式）

### 3.3 武器 bypass 路径

| 武器/系统 | 路径 | 文件 |
|-----------|------|------|
| Spore 近战武器 (melee) | `UnsafeHealthHelper.setHealth()` 直写 + `Entity.hurt()` | `SporeToolsBaseItem.java` |
| Spore 弓 | 同上 + 百分比真伤 | `InfectedGreatBow.java` |
| Spore 十字弩 | 同上 | `InfectedCrossbow.java` |
| Spore 砍刀 (spin) | `UnsafeHealthHelper.setHealth()` 直写 (每 5 tick) | `InfectedCleaver.java` |
| 生物近战 (doHurtTarget) | 通道1: `UnsafeHealthHelper.setHealth()` bypass + 通道2: normal hurt | `Infected.java` / `UtilityEntity.java` |
| 百分比伤害 AOE | `SporeDamageDispatcher.dealPercentageDamage()` | `SporeDamageDispatcher.java` |
| 极寒附魔真伤 | `HealthFieldUtil.addHealth()` (CoreModHooks.applyTrueDamage) | `ExtremeFrostEnchantment.java` / `CoreModHooks.java` |
| EntityActuallyHurt | `UnsafeHealthHelper.setHealth()` 绕过无敌帧 | `EntityActuallyHurtUtil.java` |

### 3.4 SporeDamageDispatcher 百分比伤害

```
dealPercentageDamage(Mob attacker, LivingEntity target, float baseDamage)
  └─ 根据攻击者类型获取百分比：
      灾难级 → calamity_percentage_damage (配置)
      超进化 → hyper_percentage_damage
      进化级 → evolved_percentage_damage
      其他   → default_percentage_damage
  └─ UnsafeHealthHelper.setHealth() 直写改血
```

### 3.5 生物 doHurtTarget 双通道

```java
// 通道1: UnsafeHealthHelper 直写 bypass（跳过创造模式玩家、Spore 护甲）
if (enable_force_set_health && !hasSporeArmor && !creative) {
    UnsafeHealthHelper.setHealth(livingTarget, newHealth);
    forceBypass = true;
}
// 通道2: 普通 hurt（当通道1被条件禁止时作为回退）
flag = entity.hurt(getCustomDamage(this), f) || forceBypass;
```

## 四、真实无敌帧系统

字节码级硬闸，在 hurt() / setHealth() 最开头通过 ASM 注入拦截。

| 机制 | 持续时间 | 效果 |
|------|----------|------|
| hurt 入口检查 | 4 tick (0.2s) | 阻挡所有伤害 |
| hurt 返回设置 | 4 tick | 每次有效 hit 刷新 |
| limitSetHealth 层 | 4 tick | 阻挡扣血 |

Spore 武器/生物通过 `CoreModHooks.clearRealInvuln()` 绕过后，执行 bypass 或真伤。

## 五、禁疗机制

- `spore_frost_antiheal` NBT 标记 + `spore_frost_antiheal_time` 过期时间
- `CoreModHooks.shouldBlockHeal()` 在 `LivingHealEvent` 中拦截
- Spore 实体免疫禁疗（自动清除标记）
- 非 Spore 实体保持禁疗直到时间过期

## 六、血量影子备份

```java
CoreModHooks.recordHealthBackup()  // limitSetHealth 扣血通过时记录
CoreModHooks.checkHealthConsistency()  // 每 tick 校验，偏差 > 0.5f 恢复
```

影子备份 200 tick (10s) 过期，用于检测 Unsafe 直写 DataItem.value 等绕过。

## 七、Boss 专用防御 (SporeBossDefenseSystem)

| 特性 | 值 |
|------|-----|
| 默认伤害减免 | 75% |
| 无敌帧阶段 | [20, 40, 60] ticks (1/2/3 秒) |
| 异常伤害阈值 | 5% 最大血量 |
| 阶段3防御 | 完全免疫伤害 |
| 自动回血 | 可配置速率和持续时间 |

## 八、Boss AOE 攻击系统 (SporeBossAOESystem)

持续范围伤害，每 tick 执行：
1. 10% 概率 `setMaxHealth(0)` + `applyTrueDamage()` 斩杀
2. `EntityActuallyHurtUtil.actuallyHurt()` 绕过无敌帧
3. `target.hurt()` 多种伤害源连击 (5-14次)
4. 重置目标无敌帧 + 冻结位置
5. 真伤补底: `maxHealth * 2% + 1`

排除：创造模式玩家、Spore 实体、持有极寒附魔的目标。

## 九、配置项 (SConfig.SERVER)

| 配置 | 影响 |
|------|------|
| `enable_damage_limit` | 全局限伤开关 |
| `enable_percentage_damage` | 百分比伤害开关 |
| `default_percentage_damage` | 默认百分比伤害 |
| `evolved_percentage_damage` | 进化级百分比 |
| `hyper_percentage_damage` | 超进化百分比 |
| `calamity_percentage_damage` | 灾难级百分比 |
| `bypass_armor` | 穿透护甲开关 |
| `bypass_creative` | 穿透创造开关 |
| `enable_force_set_health` | 生物 bypass 直写开关 |
| `force_health_reduction` | bypass 直写固定伤害值 |
| `default_damage_limit` | 默认帧伤上限 |
| `boss_damage_limit` | Boss 帧伤上限 |
| `enable_damage_limit_anticheat` | 反作弊增强模式 |

## 十、变更记录

### 2026-06-07
- 创建完整防御机制文档
- 记录 HealthFieldUtil→UnsafeHealthHelper 转换
- 记录创造模式白名单（7 个站点）
- 记录 SporeHealthStorage 0值回退修复
