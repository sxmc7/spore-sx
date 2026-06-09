package com.Harbinger.Spore.util;

import com.Harbinger.Spore.Spore;
import com.Harbinger.Spore.capability.CustomHealthRegistry;
import com.Harbinger.Spore.capability.ICustomHealth;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 血量直写工具 — Delta 探测版。
 *
 * MC 1.20.1 关键事实：
 *   getHealth()  = entityData.get(DATA_HEALTH_ID)   ← 从 SynchedEntityData 读
 *   setHealth(f) = entityData.set(DATA_HEALTH_ID, f) ← 写入 SynchedEntityData
 *   原始字段 health (f_20920_) 不参与运行时 get/set，仅用于 NBT 序列化。
 *
 * 攻击链（逐层增强）：
 *   1) Capability 实体 → cap.setCustomHealth()
 *   2) Unsafe 直写 DataItem.value（绕过 SynchedEntityData.set）
 *   3) HealthFieldUtil 补充写入（accessor/VarHandle 三层绕过）
 *   4) Delta 探测自定义字段 → Unsafe 直写（路径 2+3 无效时的保底）
 *
 * 第 4 层解决"目标通过 Mixin 覆写 getHealth() 返回自定义字段"的问题。
 * 原理：写测试值 → 调 getHealth() 验证变化 → 确定真实血量字段 → 缓存偏移。
 */
public final class UnsafeHealthHelper {

    private static final Unsafe UNSAFE;

    static {
        Unsafe u = null;
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u = (Unsafe) f.get(null);
        } catch (Exception e) {
            Spore.LOGGER.error("[UnsafeHealthHelper] Failed to get Unsafe instance", e);
        }
        UNSAFE = u;
    }

    /** Unsafe 是否可用 */
    public static boolean isAvailable() {
        return UNSAFE != null;
    }

    // ======== Unsafe 直写 DataItem.value 缓存 ========
    private static final Object UNSAFE_DATAITEM_LOCK = new Object();
    private static volatile Field ITEMS_BY_ID_CACHE;
    private static volatile long DATAITEM_VALUE_OFFSET = -1;

    /** 获取实体的 Capability（若可用） */
    private static ICustomHealth getCap(LivingEntity entity) {
        if (CustomHealthRegistry.CUSTOM_HEALTH == null) return null;
        return entity.getCapability(CustomHealthRegistry.CUSTOM_HEALTH).orElse(null);
    }

    // ======== Unsafe 直写 SynchedEntityData.DataItem.value ========

    /**
     * 在 SynchedEntityData 中扫描 itemsById Map（映射无关）。
     * 通过查找 Map<?, DataItem> 类型字段识别。
     */
    private static Map<?, SynchedEntityData.DataItem<?>> getItemsById(SynchedEntityData data) {
        Field f = ITEMS_BY_ID_CACHE;
        if (f == null) {
            synchronized (UNSAFE_DATAITEM_LOCK) {
                f = ITEMS_BY_ID_CACHE;
                if (f == null) {
                    for (Field field : SynchedEntityData.class.getDeclaredFields()) {
                        if (Map.class.isAssignableFrom(field.getType())) {
                            field.setAccessible(true);
                            try {
                                Object mapObj = field.get(data);
                                if (mapObj instanceof Map) {
                                    Map<?, ?> map = (Map<?, ?>) mapObj;
                                    if (!map.isEmpty()) {
                                        Object firstVal = map.values().iterator().next();
                                        if (firstVal instanceof SynchedEntityData.DataItem) {
                                            ITEMS_BY_ID_CACHE = field;
                                            f = field;
                                            break;
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        }
        if (f == null) return null;
        try { return (Map<?, SynchedEntityData.DataItem<?>>) f.get(data); }
        catch (Exception e) { return null; }
    }

    /**
     * 在 itemsById 中查找血量 DataItem（Float 序列化器，值匹配 getHealth()）。
     * 返回最佳匹配项；若无精确匹配则返回首个 Float DataItem。
     */
    private static SynchedEntityData.DataItem<?> findHealthDataItem(
            Map<?, SynchedEntityData.DataItem<?>> itemsById, float currentHealth) {
        SynchedEntityData.DataItem<?> fallback = null;
        for (SynchedEntityData.DataItem<?> item : itemsById.values()) {
            if (item.getAccessor().getSerializer() == EntityDataSerializers.FLOAT) {
                if (fallback == null) fallback = item;
                float val = (Float) item.getValue();
                if (Math.abs(val - currentHealth) < 1.0f) {
                    return item;
                }
            }
        }
        return fallback;
    }

    /**
     * 获取 DataItem.value 字段偏移（映射无关）。
     * DataItem 有 3 个字段：accessor(EntityDataAccessor), value(Object), dirty(boolean)。
     * 非 boolean 且非 EntityDataAccessor 的字段即为 value。
     */
    private static long getDataItemValueOffset() {
        if (DATAITEM_VALUE_OFFSET != -1) return DATAITEM_VALUE_OFFSET;
        if (UNSAFE == null) return -1;
        synchronized (UNSAFE_DATAITEM_LOCK) {
            if (DATAITEM_VALUE_OFFSET != -1) return DATAITEM_VALUE_OFFSET;
            for (Field f : SynchedEntityData.DataItem.class.getDeclaredFields()) {
                if (f.getType() == boolean.class) continue;
                if (EntityDataAccessor.class.isAssignableFrom(f.getType())) continue;
                DATAITEM_VALUE_OFFSET = UNSAFE.objectFieldOffset(f);
                Spore.LOGGER.info("[UnsafeHealthHelper] DataItem.value offset={} field={}",
                        DATAITEM_VALUE_OFFSET, f.getName());
                return DATAITEM_VALUE_OFFSET;
            }
            Spore.LOGGER.warn("[UnsafeHealthHelper] DataItem.value field not found!");
        }
        return -1;
    }

    /**
     * 使用 Unsafe 直接写入 SynchedEntityData.DataItem.value，
     * 绕过所有 Java 层拦截（SynchedEntityData.set/get, Mixin, CoreMod, 反射检测, Unsafe 保护等）。
     *
     * entity.getHealth() 直接读取 DataItem.value，因此此修改即时生效。
     * 致命伤会由下一 tick 的 checkDeadA cycler() 检测到并触发死亡。
     */
    private static boolean setHealthDirectDataItem(LivingEntity entity, float health) {
        if (UNSAFE == null) return false;
        try {
            SynchedEntityData data = entity.getEntityData();
            Map<?, SynchedEntityData.DataItem<?>> itemsById = getItemsById(data);
            if (itemsById == null || itemsById.isEmpty()) return false;
            SynchedEntityData.DataItem<?> healthItem = findHealthDataItem(itemsById, entity.getHealth());
            if (healthItem == null) return false;
            long valueOffset = getDataItemValueOffset();
            if (valueOffset == -1) return false;
            UNSAFE.putObject(healthItem, valueOffset, Float.valueOf(health));
            return true;
        } catch (Exception e) {
            Spore.LOGGER.warn("[UnsafeHealthHelper] Direct DataItem write failed: {}", e.getMessage());
            return false;
        }
    }

    // ======== 读血量 ========

    /**
     * 获取实体当前血量。
     * Capability 激活 → 返回 capability 值
     * 否则 → HealthFieldUtil.getHealth() (accessor/VarHandle/setHealth 链)
     */
    public static float getHealth(LivingEntity entity) {
        if (entity == null) return 0.0f;
        ICustomHealth cap = getCap(entity);
        if (cap != null && cap.isActive()) return cap.getCustomHealth();
        return HealthFieldUtil.getHealth(entity);
    }

    // ======== 写血量 ========

    /**
     * 强制设置血量，绕过所有 setHealth 拦截。
     *
     * 攻击链：
     *   1) Capability 实体 → cap.setCustomHealth() 直写
     *   2) Unsafe 直写 DataItem.value（绕过 SynchedEntityData.set）
     *   3) HealthFieldUtil 补充写入（accessor/VarHandle/entity.setHealth）
     *   4) 验证 getHealth() 是否生效，否则 Delta 探测自定义字段（路径 2+3 无效时的保底）
     *
     * 不论哪条路径，Spore 实体都会额外同步 SporeHealthStorage，
     * 确保 AoE/武器伤害不走丢血量。
     */
    public static void setHealth(LivingEntity entity, float health) {
        if (entity == null) return;
        health = Math.max(0.0f, health);

        // 路径1: Capability 实体 → 直接写入 capability
        ICustomHealth cap = getCap(entity);
        if (cap != null && cap.isActive()) {
            cap.setCustomHealth(health);
        } else {
            // 路径2: Unsafe 直写 DataItem.value
            setHealthDirectDataItem(entity, health);
            // 路径3: HealthFieldUtil 补充写入（对 Spore 目标会同步 SporeHealthStorage）
            HealthFieldUtil.setHealth(entity, health);

            // 路径4: 验证 getHealth() 是否真正生效
            // 如果目标有 Mixin 覆写 getHealth() 返回自定义字段，路径 2+3 全部无效
            if (entity.isAlive() && Math.abs(entity.getHealth() - health) > 1.0f) {
                Spore.LOGGER.info("[UnsafeHealthHelper] Standard paths failed for {}, getHealth={} target={}",
                        entity.getClass().getSimpleName(), entity.getHealth(), health);
                if (!findAndWriteCustomHealth(entity, health)) {
                    // Delta 探测也失败 → 血量不在 Java 堆（堆外/XOR/自定义存储）
                    // 尝试反射调用外部 HealthStorage 写入
                    tryExternalHealthWrite(entity, health);
                }
            }
        }

        // === 统一同步：Spore 实体确保 SporeHealthStorage 一致性 ===
        // Capability 路径不会走 HealthFieldUtil（不会自动同步），需手动同步
        // 非 Capability 路径 HealthFieldUtil 已同步，再次 set 是无害的覆写
        if (entity.getClass().getName().startsWith("com.Harbinger.Spore.")) {
            com.Harbinger.Spore.Sentities.anticheat.SporeHealthStorage.setHealth(entity, health);
        }
    }

    /** 扣血（负值治疗） */
    public static void addHealth(LivingEntity entity, float amount) {
        if (entity == null || !entity.isAlive()) return;
        float current = getHealth(entity);
        if (current <= 0.0f && amount <= 0.0f) return;
        setHealth(entity, current + amount);
    }

    /** 扣百分比血量 */
    public static void damagePercentage(LivingEntity entity, float percent) {
        if (entity == null || !entity.isAlive()) return;
        setHealth(entity, getHealth(entity) * (1.0f - percent));
    }

    /** 强制击杀（设置血量为 0） */
    public static void kill(LivingEntity entity) {
        setHealth(entity, 0.0f);
    }

    // ======== 暴力清除（后备方案）========

    /**
     * 暴力清除所有正数 float/int 字段。
     *
     * 原理：某些实体将真实血量存储在自定义字段中（非标准 health 字段）。
     * 将所有合理范围内的正数数值清零以彻底击杀。
     * 可能误伤其他属性（经验值、计时器等），单机环境通常可接受。
     */
    public static void killByBruteForce(LivingEntity entity) {
        if (UNSAFE == null || entity == null) return;
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                Class<?> type = field.getType();
                try {
                    long offset = UNSAFE.objectFieldOffset(field);
                    if (type == float.class) {
                        float val = UNSAFE.getFloat(entity, offset);
                        if (val > 0 && val < 1000) {
                            UNSAFE.putFloat(entity, offset, 0f);
                            Spore.LOGGER.debug("[UnsafeHealthHelper] Brute zeroed float {}={} in {}",
                                    field.getName(), val, clazz.getSimpleName());
                        }
                    } else if (type == int.class) {
                        int val = UNSAFE.getInt(entity, offset);
                        if (val > 0 && val < 1000) {
                            UNSAFE.putInt(entity, offset, 0);
                            Spore.LOGGER.debug("[UnsafeHealthHelper] Brute zeroed int {}={} in {}",
                                    field.getName(), val, clazz.getSimpleName());
                        }
                    }
                } catch (Exception ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        // 先直写 DataItem.value 为 0（确保 getHealth() 返回 0，即使 hurt() 被拦截）
        setHealthDirectDataItem(entity, 0f);
        // 再尝试 hurt() 触发死亡逻辑
        if (entity.isAlive()) {
            entity.hurt(entity.damageSources().generic(), Float.MAX_VALUE);
        }
    }

    // ======== 终极击杀（绕过血量系统）========

    /**
     * 通过设置 Entity.removed 标志直接移除实体（不依赖血量系统）。
     *
     * 策略：在 Entity.class 中扫描所有 boolean 字段，
     * 逐一设为 true 后检查 isRemoved() 返回值来识别 removed 字段。
     * 映射无关：不依赖任何字段名。
     *
     * 用于对抗自定义 getHealth/setHealth Mixin 保护的目标。
     */
    public static boolean killByRemovalFlag(LivingEntity entity) {
        if (UNSAFE == null || entity == null) return false;
        if (entity.isRemoved()) return true;
        // 尝试标准移除（可能被拦截，但值得一试）
        try { entity.remove(Entity.RemovalReason.KILLED); } catch (Exception ignored) {}
        if (entity.isRemoved()) return true;
        // Unsafe 方式：扫描 Entity.class 中非静态 boolean 字段
        for (Field field : Entity.class.getDeclaredFields()) {
            if (field.getType() != boolean.class) continue;
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                long offset = UNSAFE.objectFieldOffset(field);
                if (UNSAFE.getBoolean(entity, offset)) continue;
                UNSAFE.putBoolean(entity, offset, true);
                if (entity.isRemoved()) {
                    Spore.LOGGER.info("[UnsafeHealthHelper] killByRemovalFlag found removed field: {}", field.getName());
                    return true;
                }
                UNSAFE.putBoolean(entity, offset, false); // 不是 removed，恢复
            } catch (Exception ignored) {}
        }
        return entity.isRemoved();
    }

    /**
     * 终极击杀 — 多重手段确保实体被移除，绕过所有血量级保护。
     *
     * 攻击链（逐层增强）：
     *   1. 标准击杀路径（kill/die/hurt/Float.MAX_VALUE）
     *   2. Unsafe 零化所有 float/int ≤ 1000（覆盖自定义血量存储字段）
     *   3. Unsafe 直写 DataItem.value = 0（覆盖标准 getHealth 数据源）
     *   4. Unsafe 设置 LivingEntity 所有 boolean = true（覆盖 dead 标志）
     *   5. Unsafe 探测并设置 Entity.removed = true（覆盖移除标志）
     *
     * 第 5 层是最终保障：不依赖任何血量相关逻辑，直接标记实体为"已移除"。
     */
    public static void ultimateKill(LivingEntity entity) {
        if (entity == null || entity.isRemoved()) return;
        // Phase 1: 标准击杀
        try { entity.kill(); } catch (Exception ignored) {}
        try { entity.die(entity.damageSources().generic()); } catch (Exception ignored) {}
        // Phase 2: 暴力零化 float/int 字段（覆盖自定义血量存储）
        killByBruteForce(entity);
        // Phase 3: LivingEntity 所有 boolean → true（覆盖 dead）
        for (Field f : LivingEntity.class.getDeclaredFields()) {
            if (f.getType() != boolean.class) continue;
            if (Modifier.isStatic(f.getModifiers())) continue;
            try { UNSAFE.putBoolean(entity, UNSAFE.objectFieldOffset(f), true); } catch (Exception ignored) {}
        }
        // Phase 4: 直接移除标志（终极保障，不依赖血量系统）
        killByRemovalFlag(entity);
    }

    // ======== 自定义血量字段探测（Delta 法）========
    // 用于对抗 Mixin 覆写 getHealth() 返回自定义字段的目标。
    // 原理：扫描实体所有 float 字段，写测试值 → 调 getHealth() 验证变化 → 确定真实血量字段。
    private static final Map<Class<?>, Long> CUSTOM_HEALTH_OFFSET_CACHE = new ConcurrentHashMap<>();

    /**
     * 探测并写入自定义血量字段。
     * 当标准路径（DataItem.value + HealthFieldUtil）无效时调用，说明目标的 getHealth()
     * 被 Mixin/CoreMod 覆写，返回的是自定义字段而非 DataItem.value。
     *
     * Delta 探测法（映射无关）：
     *   1. 扫描类层级所有 float 字段，找到值 ≈ entity.getHealth() 的候选
     *   2. 对每个候选写入测试值（+5.0f）
     *   3. 调 entity.getHealth() 看是否变化
     *   4. 恢复原值
     *   5. 如果 getHealth() 变了 → 该字段就是自定义血量字段 → 写入目标值并缓存偏移
     */
    private static boolean findAndWriteCustomHealth(LivingEntity entity, float health) {
        if (UNSAFE == null) return false;
        Class<?> entityClass = entity.getClass();

        // 1. 检查缓存
        Long cachedOffset = CUSTOM_HEALTH_OFFSET_CACHE.get(entityClass);
        if (cachedOffset != null) {
            UNSAFE.putFloat(entity, cachedOffset, health);
            Spore.LOGGER.info("[UnsafeHealthHelper] Wrote custom health field via cache: {}={}", entityClass.getSimpleName(), health);
            return true;
        }

        // 2. Delta 探测
        float currentHealth = entity.getHealth();
        Spore.LOGGER.info("[UnsafeHealthHelper] Starting delta detection for {} getHealth={}",
                entityClass.getSimpleName(), currentHealth);

        for (Class<?> clazz = entityClass; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType() != float.class) continue;
                if (Modifier.isStatic(field.getModifiers())) continue;
                try {
                    long offset = UNSAFE.objectFieldOffset(field);
                    float val = UNSAFE.getFloat(entity, offset);
                    if (Math.abs(val - currentHealth) > 0.001f) continue; // 值不匹配，跳过

                    // Delta 测试：写测试值 → 读 getHealth() → 恢复
                    float testVal = val + 5.0f;
                    UNSAFE.putFloat(entity, offset, testVal);
                    float healthAfter = entity.getHealth();
                    UNSAFE.putFloat(entity, offset, val); // 立即恢复

                    if (Math.abs(healthAfter - testVal) < 0.001f) {
                        // 命中！
                        CUSTOM_HEALTH_OFFSET_CACHE.put(entityClass, offset);
                        UNSAFE.putFloat(entity, offset, health);
                        Spore.LOGGER.info("[UnsafeHealthHelper] ✓ Delta hit! field={}.{} offset={}",
                                clazz.getSimpleName(), field.getName(), offset);
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        }

        Spore.LOGGER.warn("[UnsafeHealthHelper] ✗ Delta detection failed for {} (no float field matches getHealth={})",
                entityClass.getSimpleName(), currentHealth);
        return false;
    }

    // ======== 外部血量存储回退（堆外/XOR编码）========

    /**
     * 尝试通过外部血量存储系统写入血量。
     * 当标准路径和 Delta 探测都失败时触发，说明血量在堆外内存中
     * 或通过自定义 API 管理，不存在于任何 Java 对象的 float 字段中。
     *
     * 策略：从实体类所在包路径向上逐级退级扫描，
     * 每级尝试多种常见的血量存储类命名模式，
     * 对每个候选类尝试多种方法签名反射调用。
     */
    private static boolean tryExternalHealthWrite(LivingEntity entity, float health) {
        try {
            ClassLoader cl = entity.getClass().getClassLoader();
            if (cl == null) return false;
            String[] parts = entity.getClass().getPackage().getName().split("\\.");

            // 类名后缀模式 — 覆盖多种模组的命名习惯
            String[] suffixes = {
                ".util.HealthStorage",
                ".HealthStorage",
                ".storage.HealthStorage",
                ".data.HealthData",
                ".util.HealthData",
                ".HealthData",
                ".util.HealthManager",
                ".HealthManager",
                ".storage.HealthData",
                ".capability.HealthCapability",
                ".HealthCapability",
                ".util.CustomHealth",
                ".CustomHealth",
                ".data.Health",
                ".util.HealthStore",
                ".HealthStore",
            };

            for (int i = parts.length; i >= 0; i--) {
                StringBuilder base = new StringBuilder();
                for (int j = 0; j < i; j++) {
                    if (j > 0) base.append('.');
                    base.append(parts[j]);
                }
                String p = i == 0 ? "" : base.toString();
                for (String suffix : suffixes) {
                    if (tryHealthStorageClass(p + suffix, cl, entity, health)) return true;
                }
            }
            return false;
        } catch (Exception e) {
            Spore.LOGGER.debug("[UnsafeHealthHelper] External health write failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 反射调用血量存储类的写入方法。
     * 尝试多种方法签名和枚举结构，适配不同模组的实现差异。
     */
    private static boolean tryHealthStorageClass(String className, ClassLoader cl, LivingEntity entity, float health) {
        try {
            Class<?> hsClass = Class.forName(className, false, cl);

            // 1. 查找内部枚举（HealthType/Type/Health 等），获取 CURRENT 值
            Enum<?> currentType = findHealthTypeEnum(hsClass);
            int entityId = entity.getId();

            // 2. 按优先级尝试多种方法签名
            // 优先匹配参数数最多的，因为更精确

            // 2a. write(Object, int, Enum, float) — HealthStorage 标准签名
            if (tryMethod(hsClass, "write", new Class<?>[]{Object.class, int.class, currentType != null ? currentType.getClass() : Enum.class, float.class},
                    new Object[]{entity, entityId, currentType, health})) return true;

            // 2b. write(Object, Enum, float) — 无 entityId
            if (currentType != null && tryMethod(hsClass, "write", new Class<?>[]{Object.class, currentType.getClass(), float.class},
                    new Object[]{entity, currentType, health})) return true;

            // 2c. setHealth(Object, float) — 简化 API
            if (tryMethod(hsClass, "setHealth", new Class<?>[]{Object.class, float.class},
                    new Object[]{entity, health})) return true;

            // 2d. set(Object, float)
            if (tryMethod(hsClass, "set", new Class<?>[]{Object.class, float.class},
                    new Object[]{entity, health})) return true;

            // 2e. setHealth(Object, int, float) — entityId 版
            if (tryMethod(hsClass, "setHealth", new Class<?>[]{Object.class, int.class, float.class},
                    new Object[]{entity, entityId, health})) return true;

            // 2f. write(Object, float) — 极简
            if (tryMethod(hsClass, "write", new Class<?>[]{Object.class, float.class},
                    new Object[]{entity, health})) return true;

            // 2g. store(Object, Enum, float)
            if (currentType != null && tryMethod(hsClass, "store", new Class<?>[]{Object.class, currentType.getClass(), float.class},
                    new Object[]{entity, currentType, health})) return true;

            // 2h. modifyHealth(Object, int, float)
            if (tryMethod(hsClass, "modifyHealth", new Class<?>[]{Object.class, int.class, float.class},
                    new Object[]{entity, entityId, health})) return true;

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** 在类中查找血量类型枚举，返回 CURRENT 枚举值 */
    private static Enum<?> findHealthTypeEnum(Class<?> clazz) {
        for (Class<?> dc : clazz.getDeclaredClasses()) {
            if (!dc.isEnum()) continue;
            String name = dc.getSimpleName();
            // 匹配常见的血量类型枚举名
            if (name.contains("Type") || name.contains("Health") || name.contains("HealthType")) {
                try {
                    @SuppressWarnings("unchecked")
                    Enum<?> result = Enum.valueOf((Class<Enum>) dc, "CURRENT");
                    return result;
                } catch (IllegalArgumentException ignored) {}
            }
        }
        // 对于没有展开内部枚举的类，尝试找任何枚举的 CURRENT
        for (Class<?> dc : clazz.getDeclaredClasses()) {
            if (!dc.isEnum()) continue;
            try {
                @SuppressWarnings("unchecked")
                Enum<?> result = Enum.valueOf((Class<Enum>) dc, "CURRENT");
                return result;
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    /** 安全反射调用方法 */
    private static boolean tryMethod(Class<?> clazz, String name, Class<?>[] paramTypes, Object[] args) {
        try {
            java.lang.reflect.Method m = clazz.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            m.invoke(null, args);
            Spore.LOGGER.info("[UnsafeHealthHelper] ✓ External health write via {}.{}({}) = {}",
                    clazz.getSimpleName(), name, args[args.length - 1], args.length > 0 ? args[0].getClass().getSimpleName() : "?");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Exception e) {
            Spore.LOGGER.debug("[UnsafeHealthHelper] Method {}.{} failed: {}", clazz.getSimpleName(), name, e.getMessage());
            return false;
        }
    }

    /** 清除全部缓存 */
    public static void clearAllCache() {
        HealthFieldUtil.clearHealthFieldCache();
    }
}
