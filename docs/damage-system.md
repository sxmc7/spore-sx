# Spore 伤害/限伤/无敌帧/改血 机制详解

> 最后更新: 2026-05-10
> 适用版本: Spore 2.0.1 (Minecraft 1.20.1 Forge)

---

## 一、总体架构

伤害系统分为 **4 层**，从底层到上层依次为：

```
┌─────────────────────────────────────────────────┐
│  第4层: 反作弊层 (DamageLimiter / HealthManager) │  ← 软限制、帧保护
├─────────────────────────────────────────────────┤
│  第3层: Mixin层 (CustomHealthMixin)             │  ← 方法劫持 + Capability 存储
├─────────────────────────────────────────────────┤
│  第2层: CoreMod字节码层 (ASM 硬编码)            │  ← 无敌帧硬门 + 1% 限伤
├─────────────────────────────────────────────────┤
│  第1层: 工具绕过层 (UnsafeHealthHelper)          │  ← 直写内存，零拦截
└─────────────────────────────────────────────────┘
```

**核心原则：**
- Spore 生物的血量最终由 `ICustomHealth Capability` 决定（原版 `LivingEntity.health` 字段是诱饵）
- 所有伤害/治疗最终会通过 `CustomHealthMixin` 落地到 Capability
- 工具层（Unsafe/VarHandle）在 Capability 激活时优先读写 Capability
- CoreMod 字节码在类加载时注入，任何 Mixin 都无法绕过

---

## 二、CustomHealth Capability — 血量解耦

### 接口: `ICustomHealth`

```java
float getCustomHealth();
void setCustomHealth(float health);   // 自动 clamp [0, maxHealth]
float getCustomMaxHealth();
void setCustomMaxHealth(float maxHealth); // 最低 1.0
boolean isActive();
void setActive(boolean active);
```

### 实现: `CustomHealthCapability`

三字段：`customHealth` / `customMaxHealth` / `active`

- **active = true**: Spore 生物。全部血量操作落地到 Capability，原版 health 字段废弃
- **active = false**: 非 Spore 生物。Capability 不介入，走原版逻辑

### 注册: `CustomHealthRegistry`

- `CapabilityManager.get(new CapabilityToken<>() {})` 获取 Capability 实例
- `RegisterCapabilitiesEvent` 注册能力（MOD 总线）
- `AttachCapabilitiesEvent<Entity>` 为所有 `LivingEntity` 附着 Provider（FORGE 总线）
- Spore 生物判定: `entity.getClass().getName().startsWith("com.Harbinger.Spore.")`
- NBT 序列化存储 `Health / MaxHealth / Active` 三个字段

---

## 三、CustomHealthMixin — 全方法劫持

优先级 1100（高于默认），4 个注入点：

### 3.1 getHealth `@Inject(RETURN, cancellable)`

```java
// Capability 激活时 → 返回 cap.getCustomHealth()
// 未激活 → 返回原方法值（原版 health 字段）
```

**效果**：任何调用 `entity.getHealth()` 的地方，对 Spore 生物都得到 Capability 中的血量。

### 3.2 setHealth `@Inject(HEAD, cancellable)`

```java
// CAP 激活时:
//   1. 计算 change = current - incoming(正数=扣血)
//   2. 如果扣血 > maxHealth * 5%，限制到 maxHealth * 5%
//   3. 写入 cap.setCustomHealth(clamped)
//   4. cancel 原方法体（原字段不变）
//
// 未激活 → return，放行原方法体
```

**效果**：Spore 生物任何 setHealth 调用单次最多扣 5% 最大血量。

### 3.3 hurt `@Inject(HEAD, cancellable)`

完整执行流程：

```java
1. 重入守卫检查 (HURT_LOCK) → 如果已在重入中，放行原方法体
2. 获取 Capability → 未激活则 passthrough
3. 无敌帧检查 → invulnerableTime > 0 且非 BYPASSES_INVULNERABILITY 则 cancel
4. 5% 限伤 → min(原始伤害, maxHealth * 5%)
5. 扣血 → cap.setCustomHealth(current - actualDamage)
6. 设置 invulnerableTime = 10
7. 调 self.hurt(source, 0.0f) → 触发游戏事件（HURT_LOCK 守卫防止递归）
8. 如果血量 ≤ 0 → self.die(source)
9. Cancel 原方法体，返回 result
```

**重入守卫**：`ThreadLocal<Boolean> HURT_LOCK`，在调用 `self.hurt(source, 0.0f)` 期间设为 true，防止无限递归。

### 3.4 getMaxHealth `@Inject(RETURN, cancellable)`

Capability 激活时返回 `cap.getCustomMaxHealth()`。

---

## 四、CoreMod 字节码层 (硬编码，无法绕过)

通过 `SporeLaunchPlugin` (ILaunchPluginService) 在类加载时注入，比任何 Mixin 都早执行。

### 4.1 真实无敌帧 (Real Invuln) — `CoreModHooks.checkRealInvuln()`

**注入位置**：`LivingEntity.hurt()` 第一行字节码。

```java
public static boolean checkRealInvuln(LivingEntity entity) {
    if (entity == null || entity.level() == null) return false;
    CompoundTag tag = entity.getPersistentData();
    if (!tag.contains("spore_real_invuln")) return false;
    return tag.getInt("spore_real_invuln") > entity.tickCount;
}
```

逻辑：
- 读取 NBT 键 `spore_real_invuln`，值是 **未来 tick 数**
- 如果当前 tick < 存储值 → 返回 true → `hurt()` 立即 return false
- 持续时间: **4 ticks (0.2 秒)**

**设置时机**：`CoreModHooks.onHurtReturn()` — 当 hurt() 成功造成伤害时，写入 `spore_real_invuln = tickCount + 4`。

**清除机制**：`CoreModHooks.clearRealInvuln(entity)` — 移除 NBT 键。由极寒附魔和 Spore 武器调用，实现"无视无敌帧"。

### 4.2 setHealth 限伤 — `CoreModHooks.limitSetHealth()`

**注入位置**：`LivingEntity.setHealth(float)` 第一行字节码。

```java
public static float limitSetHealth(LivingEntity entity, float incoming) {
    // HealthFieldUtil 授权写入 → 放行
    if (HealthFieldUtil.isAuthorized()) return incoming;

    // 真实无敌帧激活且调用者不是 Spore 生物 → 禁止改写
    if (checkRealInvuln(entity)) {
        Class<?> caller = getCallerClass();
        if (caller == null || !caller.getName().startsWith("com.Harbinger.Spore.")) {
            return entity.getHealth(); // 拒绝修改
        }
    }

    // 扣血量限制: 单次最多扣当前血量的 1%
    float current = entity.getHealth();
    if (incoming < current) {
        float maxAllowed = Math.max(1.0f, current * 0.01f);
        if (current - incoming > maxAllowed) {
            return current - maxAllowed; // 限制下降量
        }
    }

    return incoming;
}
```

三层保护：
1. **授权写入**: `HealthFieldUtil.isAuthorized()` 为 true 时直接放行
2. **无敌帧保护**: 真实无敌帧未过且非 Spore 调用者 → 拒绝任何 setHealth 修改
3. **1% 限伤**: 任何 setHealth 扣血最多扣当前血量的 1%

### 4.3 治疗拦截 — `CoreModHooks.shouldBlockHeal()`

**注入位置**：`LivingEntity.heal(float)` 第一行字节码。

```java
public static boolean shouldBlockHeal(LivingEntity entity) {
    return entity.getPersistentData().getBoolean("spore_frost_antiheal")
           && !entity.getClass().getName().startsWith("com.Harbinger.Spore.");
}
```

- 由 **极寒附魔 (Extreme Frost)** 设置 `spore_frost_antiheal = true`
- Spore 生物免疫此效果

### 4.4 时间停止冻结 — `CoreModHooks.isTimeStopped()`

**注入位置**：`LivingEntity.travel()` 和 `Mob.serverAiStep()` 开头。

检查 NBT `spore_freeze_until`，若当前 tick 小于存储值则阻止移动和 AI。

### 4.5 服务器 Tick 钩子 — `CoreModHooks.tickServer()`

**注入位置**：`MinecraftServer.tick()` 中。

```java
SporeEntityRegistry.tickDamageLimiters();  // 每 tick 清除限伤计数器
HealthFieldUtil.tickBleed(server);          // 持续伤害 (Bleed) 系统
```

---

## 五、工具绕过层 (Unsafe / VarHandle / Accessor)

### 5.1 UnsafeHealthHelper

`sun.misc.Unsafe` 直写/直读 `LivingEntity.health` 字段内存。

```java
// 获取 health 字段内存偏移
long offset = UNSAFE.objectFieldOffset(field);

// 直读
float val = UNSAFE.getFloat(entity, offset);

// 直写
UNSAFE.putFloat(entity, offset, newHealth);
```

**Capability 感知（新增）**：
- `getHealth()`: 先检查 Capability 是否激活 → 激活则返回 `cap.getCustomHealth()`
- `setHealth()`: 先检查 Capability → 激活则写入 Capability 并触发死亡
- 未激活时走原 Unsafe 直写路径

**启发式字段定位**：遍历类层级所有 float 字段，取当前值在 (0, 500) 范围内的作为血量字段。offset 按 (class, fieldName) 二级缓存。

### 5.2 HealthFieldUtil

四层回退架构（已整合 Unsafe 作为 Layer 0）：

```
Layer 0: UnsafeHealthHelper    (最快，零拦截)
Layer 1: SynchedEntityData      (accessor 缓存)
Layer 2: VarHandle              (health 字段)
Layer 3: entity.setHealth()     (最终回退)
```

**授权标志**：`ThreadLocal<Boolean> AUTHORIZED` — HealthFieldUtil 执行 setHealth 时设为 true，允许绕过 `CoreModHooks.limitSetHealth` 的真实无敌帧保护和 1% 限伤。

**持续伤害系统 (Bleed)**：
- `Map<UUID, float[]> BLEEDING` 存储所有流血目标
- `applyBleed()`: 叠加式，damagePerTick 累加，duration 取最大值
- `tickBleed()`: 每 tick 由 CoreMod 的 tickServer 钩子调用

### 5.3 EntityUtil.forceHurt

`forceHurt()` 是高级伤害入口，流程：

```java
1. Spore 护甲检查 → 如有护甲走原版 hurt()
2. 无敌帧检查 (DamageLimiter)
3. Boss 特殊处理 → 走原版 hurt()
4. 创造模式保护
5. 计算最终伤害（基础 + 百分比）
6. 防御减免计算（护甲 + 魔法抗性）
7. UnsafeHealthHelper 直写血量（走 Capability 感知路径）
8. 追伤标记 (spore_hp_keeper) — 后续 20 tick 持续扣血
9. 击退
10. 死亡检查 → discard()
11. DamageLimiter 记录受伤时间
```

---

## 六、反作弊层 (DamageLimiter / HealthManager)

### 6.1 DamageLimiter

```java
// 每 tick 伤害上限
perFrameDamage += damage;
if (perFrameDamage > damageCap) {
    damage = damageCap - (perFrameDamage - damage);
    // 伤害被限制
}

// 无敌帧保护 (10 ticks)
if (isInInvincibilityFrame(entity)) {
    return;
}
Map<LivingEntity, Integer> invulnTimes: 记录每个实体受伤 tick
10 tick 内再次受伤 → 跳过

// Spore 护甲双模式
if (hasSporeArmor(entity)) {
    Spore 攻击者 → 无上限（普通伤害）
    非 Spore 攻击者 → 1% 最大血量 / tick 上限
}
```

### 6.2 HealthManager / HealthRegainSystem

Boss 血量管理：
- 跟踪 Boss 血量变化
- 检测异常低血量（低于 0.1）→ 恢复到上一个健康值
- 持续自愈（0.01% 最大血量 / tick）
- 网络包同步 `S2CSyncBossHealth`

### 6.3 访问控制

- `AccessChecker`: 堆栈追踪验证调用者包名
- `ProtectedHashMap` / `ProtectedWeakHashMap`: 在 remove/put 操作前检查访问权限
- `StackChecker`: 检测 Mixin 注入帧、缓存调用者类

---

## 七、伤害流程全景图

### 场景：Spore 生物被非 Spore 武器攻击

```
外部模组调用 entity.hurt(source, 100)
  │
  ├─ [CoreMod 字节码] checkRealInvuln()
  │   └─ spore_real_invuln > tickCount → return false（跳过伤害）
  │
  ├─ [Mixin CustomHealthMixin] onHurt(HEAD, cancel)
  │   ├─ invulnerableTime > 0? → cancel
  │   ├─ 5% 限伤: actualDamage = min(100, maxHealth * 5%)
  │   ├─ cap.setCustomHealth(current - actualDamage)
  │   ├─ invulnerableTime = 10
  │   ├─ self.hurt(source, 0) → 游戏事件（重入守卫放行）
  │   └─ health ≤ 0 → die(source)
  │
  ├─ [CoreMod 字节码] onHurtReturn() — 实际伤害 > 0 时
  │   └─ spore_real_invuln = tickCount + 4
  │
  └─ [DamageLimiter] 记录 perFrameDamage
      └─ 超过 cap → 下次限制
```

### 场景：Spore 武器攻击 Spore 生物

```
SporeItems.hitEntity() → HealthFieldUtil.setHealth(entity, health - damage)
  │
  ├─ AUTHORIZED.set(true)  ← 授权标志
  ├─ UnsafeHealthHelper.setHealth()
  │   ├─ Capability 激活 → cap.setCustomHealth(newHealth)
  │   └─ health ≤ 0 → entity.die()
  └─ AUTHORIZED.set(false)
```

### 场景：EntityUtil.forceHurt 真伤绕过

```
forceHurt(attacker, target, forceDamageSource)
  │
  ├─ Spore 护甲检查 → 有护甲走原版 hurt()
  ├─ DamageLimiter.isInInvincibilityFrame() → 无敌帧跳过
  ├─ 计算最终伤害（护甲穿透 + 百分比）
  └─ UnsafeHealthHelper.setHealth()
      └─ Capability 激活 → cap.setCustomHealth(newHealth)
```

### 场景：极寒附魔/Spore武器无视无敌帧

```
ExtremeFrostEnchantment / SporeWeapon
  │
  ├─ CoreModHooks.clearRealInvuln(entity)
  │   └─ 删除 NBT: spore_real_invuln
  │     清除 CustomHealthMixin 的 invulnerableTime
  └─ 直接造成伤害
```

---

## 八、配置参数

| 参数 | 值 | 位置 |
|------|-----|------|
| CoreMod 无敌帧 | 4 ticks (0.2s) | `CoreModHooks.onHurtReturn()` |
| DamageLimiter 无敌帧 | 10 ticks (0.5s) | `DamageLimiter` |
| DamageLimiter 每 tick 上限 | 视实体类型 (5.0/7.5/10.0) | `SporeEntityRegistry` |
| Mixin 5% 限伤 (setHealth) | `maxHealth * 5%` | `CustomHealthMixin.onSetHealth` |
| Mixin 5% 限伤 (hurt) | `maxHealth * 5%` (最低 1.0) | `CustomHealthMixin.onHurt` |
| CoreMod 1% 限伤 | `currentHealth * 1%` (最低 1.0) | `CoreModHooks.limitSetHealth` |
| Spore 护甲限伤 (非 Spore 攻击者) | `maxHealth * 1%` | `DamageLimiter` |
| Boss 伤害上限 | `maxHealth / 20` | `SporeBossEntity.getDamageCap()` |
| 抗寒附魔禁止治疗 | NBT flag | `CoreModHooks.shouldBlockHeal` |

---

## 九、关键结论

1. **Spore 生物血量实际存储在 Capability 中**，原版 LivingEntity.health 字段是诱饵
2. **无敌帧有两层**：CoreMod 硬门 (4 ticks) + DamageLimiter 软门 (10 ticks)
3. **5% 限伤有两层**：Mixin (5% max HP) + CoreMod (1% current HP)
4. **Spore 武器和极寒附魔可清除无敌帧**：`clearRealInvuln()`
5. **Unsafe 直写也会走 Capability**（Capability 激活时自动路由）
6. **Boss 有独立的反作弊系统**：HealthManager + 网络包同步 + 异常检测
