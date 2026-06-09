package com.Harbinger.Spore.util;

import com.Harbinger.Spore.Spore;
import com.Harbinger.Spore.capability.CustomHealthRegistry;
import com.Harbinger.Spore.capability.ICustomHealth;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * 统一血量操作工具 — 三层绕过架构：
 *   Layer 1 — SynchedEntityData accessor: data.set(acc, health)，绕过所有 setHealth/getHealth Mixin
 *   Layer 2 — VarHandle health field: 直写字段，绕过 setHealth 方法拦截
 *   Layer 3 — entity.setHealth(): 最终回退
 *
 * Accessor 按实体类缓存（不同类各探测一次），VarHandle 全局共享。
 * 非 Spore 生物的写入走旧路径（accessor/VarHandle/setHealth + AUTHORIZED）。
 * Spore 生物的 Capability 写入走 UnsafeHealthHelper（由 EntityUtil.forceHurt 调用）。
 * 注意：HealthFieldUtil 本身不直接使用 UnsafeHealthHelper。
 */
public final class HealthFieldUtil {
    // -- Layer 1 --
    private static volatile EntityDataAccessor<Float> HEALTH_ACCESSOR;  // 当前缓存的 accessor
    private static volatile Class<?> ACCESSOR_CLASS;                     // 缓存对应的实体类

    // -- Layer 2 --
    private static volatile VarHandle HEALTH_HANDLE;                     // LivingEntity.health 字段

    // -- 全局 --
    // ThreadLocal 授权标志 — HealthFieldUtil 执行 setHealth/addHealth 时设为 true，
    // 允许绕过 CoreModHooks.limitSetHealth 的真实无敌帧保护和限伤。
    private static final ThreadLocal<Boolean> AUTHORIZED = ThreadLocal.withInitial(() -> false);

    /** 当前线程是否处于 HealthFieldUtil 授权写入状态 */
    public static boolean isAuthorized() {
        return AUTHORIZED.get();
    }

    /** 获取实体的 Capability（若可用且激活） */
    private static ICustomHealth getCustomHealth(LivingEntity entity) {
        if (CustomHealthRegistry.CUSTOM_HEALTH == null) return null;
        return entity.getCapability(CustomHealthRegistry.CUSTOM_HEALTH).orElse(null);
    }

    /** 读取血量 (accessor → VarHandle → entity.getHealth) */
    public static float getHealth(LivingEntity entity) {
        if (entity == null) return 0.0f;
        // Spore 实体 Capability 激活时 → Mixin 路由，跳过 init 避免损坏
        ICustomHealth cap = getCustomHealth(entity);
        if (cap != null && cap.isActive()) {
            return entity.getHealth(); // Mixin → cap.getCustomHealth()
        }
        // Layer 1: SynchedEntityData accessor（最直接）
        if (entity.getClass() == ACCESSOR_CLASS && HEALTH_ACCESSOR != null) {
            try { return entity.getEntityData().get(HEALTH_ACCESSOR); } catch (Exception ignored) {}
        }
        // Layer 2: VarHandle 字段直读
        VarHandle h = HEALTH_HANDLE;
        if (h != null) return (float) h.get(entity);
        // 初始化
        init(entity);
        // 重试 accessor
        if (entity.getClass() == ACCESSOR_CLASS && HEALTH_ACCESSOR != null) {
            try { return entity.getEntityData().get(HEALTH_ACCESSOR); } catch (Exception ignored) {}
        }
        // 重试 VarHandle
        h = HEALTH_HANDLE;
        if (h != null) return (float) h.get(entity);
        // Layer 3: 最终回退
        return entity.getHealth();
    }

    /** 设置血量 (accessor → VarHandle → entity.setHealth) */
    public static void setHealth(LivingEntity entity, float health) {
        if (entity == null) return;
        health = Math.max(0.0f, health);

        // Spore 实体 Capability 激活时 → 直走 Mixin 路径，跳过 init 避免损坏
        ICustomHealth cap = getCustomHealth(entity);
        if (cap != null && cap.isActive()) {
            AUTHORIZED.set(true);
            try {
                entity.setHealth(health); // CoreMod 看到 AUTHORIZED 绕过 1% 限伤, Mixin 路由到 Capability
            } finally {
                AUTHORIZED.set(false);
            }
            return;
        }

        AUTHORIZED.set(true);
        try {
            boolean wroteAccessor = false;
            boolean isSporeTarget = entity.getClass().getName().startsWith("com.Harbinger.Spore.");
            if (entity.getClass() == ACCESSOR_CLASS && HEALTH_ACCESSOR != null) {
                try { entity.getEntityData().set(HEALTH_ACCESSOR, health); wroteAccessor = true; } catch (Exception ignored) {}
            }
            VarHandle h = HEALTH_HANDLE;
            if (h != null) {
                h.set(entity, health);
                if (isSporeTarget) {
                    com.Harbinger.Spore.Sentities.anticheat.SporeHealthStorage.setHealth(entity, health);
                    com.Harbinger.Spore.Spore.LOGGER.info("[HFA] VarHandle+存储同步 {} setHealth({})",
                            entity.getClass().getSimpleName(), String.format("%.1f", health));
                }
                return;
            }
            if (wroteAccessor) {
                if (isSporeTarget) {
                    com.Harbinger.Spore.Sentities.anticheat.SporeHealthStorage.setHealth(entity, health);
                    com.Harbinger.Spore.Spore.LOGGER.info("[HFA] Accessor+存储同步 {} setHealth({})",
                            entity.getClass().getSimpleName(), String.format("%.1f", health));
                }
                return;
            }

            init(entity);

            if (entity.getClass() == ACCESSOR_CLASS && HEALTH_ACCESSOR != null) {
                try { entity.getEntityData().set(HEALTH_ACCESSOR, health); wroteAccessor = true; } catch (Exception ignored) {}
            }
            h = HEALTH_HANDLE;
            if (h != null) {
                h.set(entity, health);
                if (isSporeTarget) {
                    com.Harbinger.Spore.Sentities.anticheat.SporeHealthStorage.setHealth(entity, health);
                }
                return;
            }
            if (wroteAccessor) {
                if (isSporeTarget) {
                    com.Harbinger.Spore.Sentities.anticheat.SporeHealthStorage.setHealth(entity, health);
                }
                return;
            }

            // Layer 3: 最终回退 → 会触发 setHealth 重写（同步自动）
            entity.setHealth(health);
        } finally {
            AUTHORIZED.set(false);
        }
    }

    /** 增加血量（治疗）。amount 为负值时扣血。 */
    public static void addHealth(LivingEntity entity, float amount) {
        AUTHORIZED.set(true);
        try {
            if (entity == null || !entity.isAlive()) return;
            float current = getHealth(entity);
            if (current <= 0.0f && amount <= 0.0f) return;
            setHealth(entity, current + amount);
        } finally {
            AUTHORIZED.set(false);
        }
    }

    /** 扣除当前血量的百分比 */
    public static void damagePercentage(LivingEntity entity, float percent) {
        if (entity == null || !entity.isAlive()) return;
        setHealth(entity, getHealth(entity) * (1.0f - percent));
    }

    /** 清除所有缓存，强制下次操作时重新探测 */
    public static void clearHealthFieldCache() {
        HEALTH_ACCESSOR = null;
        ACCESSOR_CLASS = null;
        HEALTH_HANDLE = null;
        ITEMS_BY_ID_FIELD = null;
    }

    // ======== 初始化 ========

    private static void init(LivingEntity entity) {
        Class<?> clazz = entity.getClass();
        // 该实体类已有缓存
        if (clazz == ACCESSOR_CLASS) return;

        synchronized (HealthFieldUtil.class) {
            if (clazz == ACCESSOR_CLASS) return;

            // Layer 1: SynchedEntityData accessor（按类探测，每个类尝试一次）
            EntityDataAccessor<Float> acc = detectAccessor(entity);
            if (acc != null) {
                HEALTH_ACCESSOR = acc;
                ACCESSOR_CLASS = clazz;
                Spore.LOGGER.info("[HealthFieldUtil] ✓ Accessor init SUCCESS for {} id={}", clazz.getSimpleName(), acc.getId());
                return;
            }

            // 标记该类已尝试（避免重复探测），但不设 HEALTH_ACCESSOR
            ACCESSOR_CLASS = clazz;

            // Layer 2: VarHandle（全局一次性）
            if (HEALTH_HANDLE == null) {
                try {
                    Field field = detectField(entity);
                    field.setAccessible(true);
                    HEALTH_HANDLE = MethodHandles.privateLookupIn(
                            LivingEntity.class, MethodHandles.lookup()
                    ).unreflectVarHandle(field);
                    Spore.LOGGER.info("[HealthFieldUtil] ✓ VarHandle init, field={}", field.getName());
                } catch (Exception e) {
                    Spore.LOGGER.error("[HealthFieldUtil] ✗ init failed, using fallback: {}", e.getMessage());
                }
            }
        }
    }

    // ======== SynchedEntityData Accessor 探测 ========

    /**
     * 在 SynchedEntityData 中查找 entity.getHealth() 实际读取的 DataAccessor<Float>。
     * 通过按类型匹配寻找 itemsById 字段（无需知道 SRG/obfuscated 字段名）。
     * 双策略：
     *   1) 比较法：值与 entity.getHealth() 相符的即是（无副作用）
     *   2) delta法：写入测试值后比较 getHealth() 变化（多个候选值相同时区分）
     */
    private static volatile Field ITEMS_BY_ID_FIELD;

    private static Field getItemsByIdField(SynchedEntityData data) {
        Field f = ITEMS_BY_ID_FIELD;
        if (f != null) return f;
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
                                ITEMS_BY_ID_FIELD = field;
                                return field;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static EntityDataAccessor<Float> detectAccessor(LivingEntity entity) {
        SynchedEntityData data = entity.getEntityData();

        Collection<SynchedEntityData.DataItem<?>> allItems;
        try {
            Field f = getItemsByIdField(data);
            if (f == null) {
                Spore.LOGGER.warn("[HealthFieldUtil] Cannot locate itemsById Map in SynchedEntityData");
                return null;
            }
            Object map = f.get(data);
            allItems = ((Map<?, SynchedEntityData.DataItem<?>>) map).values();
        } catch (Exception e) {
            Spore.LOGGER.warn("[HealthFieldUtil] Cannot access itemsById: {}", e.getMessage());
            return null;
        }
        if (allItems == null || allItems.isEmpty()) return null;

        List<EntityDataAccessor<Float>> candidates = new ArrayList<>();
        float health = entity.getHealth();

        // 策略1：比较法 — 值与 getHealth() 相符的即是血量 accessor
        for (SynchedEntityData.DataItem<?> item : allItems) {
            if (item.getAccessor().getSerializer() == EntityDataSerializers.FLOAT) {
                EntityDataAccessor<Float> acc = (EntityDataAccessor<Float>) item.getAccessor();
                float val = (Float) item.getValue();
                Spore.LOGGER.info("[HealthFieldUtil]  accessor id={} val={} getHealth()={}", acc.getId(), val, health);
                if (Math.abs(val - health) < 1.0f) {
                    candidates.add(acc);
                }
            }
        }

        Spore.LOGGER.info("[HealthFieldUtil] detectAccessor: {} candidates for {}", candidates.size(), entity.getClass().getSimpleName());
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        // 策略2：delta法 — 多个候选值相同时，写入测试值看哪个影响 getHealth()
        Spore.LOGGER.info("[HealthFieldUtil] {} candidates match by value, trying delta...", candidates.size());
        for (EntityDataAccessor<Float> acc : candidates) {
            float before = data.get(acc);
            float testVal = before + 5.0f;
            data.set(acc, testVal);
            float afterGet = entity.getHealth();
            float afterRead = data.get(acc); // 直接读 accessor（不受 getHealth() 覆写影响）
            data.set(acc, before);
            if (Math.abs(afterGet - testVal) < 0.001f) {
                Spore.LOGGER.info("[HealthFieldUtil]  → accessor match by delta! id={}", acc.getId());
                return acc;
            }
            // 对覆写 getHealth() 的实体：accessor 能写能读回即视为有效
            if (Math.abs(afterRead - testVal) < 0.001f) {
                Spore.LOGGER.info("[HealthFieldUtil]  → accessor match by direct write+readback! id={}", acc.getId());
                return acc;
            }
        }

        Spore.LOGGER.warn("[HealthFieldUtil] delta inconclusive, returning first candidate id={}", candidates.get(0).getId());
        return candidates.get(0);
    }

    // ======== 字段级别探测（accessor 探测失败时的回退） ========

    /** 字段探测双策略：mark → delta */
    private static Field detectField(LivingEntity entity) throws Exception {
        if (entity == null) throw new NullPointerException("entity is null");
        Spore.LOGGER.info("[HealthFieldUtil] detectField starting, entity={} getHealth()={}",
                entity.getClass().getName(), entity.getHealth());

        // Strategy 1: 标记法 — 不受 getHealth() 拦截影响
        try {
            Field markMatch = detectFieldByMark(entity);
            if (markMatch != null) {
                Spore.LOGGER.info("[HealthFieldUtil] → MATCH via mark! field={}", markMatch.getName());
                return markMatch;
            }
        } catch (Exception e) {
            Spore.LOGGER.warn("[HealthFieldUtil] Mark detection failed, falling back to delta: {}", e.getMessage());
        }

        // Strategy 2: 增量法
        for (Field field : LivingEntity.class.getDeclaredFields()) {
            if (field.getType() != float.class) continue;
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            try {
                float raw1 = field.getFloat(entity);
                float gH1 = entity.getHealth();
                float testVal = raw1 + 5.0f;
                field.setFloat(entity, testVal);
                float raw2 = field.getFloat(entity);
                if (Math.abs(raw2 - testVal) >= 0.001f) {
                    field.setFloat(entity, raw1);
                    Spore.LOGGER.info("[HealthFieldUtil]  field={} val={} NOT_WRITABLE", field.getName(), raw1);
                    continue;
                }
                float gH2 = entity.getHealth();
                field.setFloat(entity, raw1);
                float fieldDelta = raw2 - raw1;
                float healthDelta = gH2 - gH1;
                Spore.LOGGER.info("[HealthFieldUtil]  field={} val={} Δfield={} Δhealth={}",
                        field.getName(), raw1, fieldDelta, healthDelta);
                if (Math.abs(healthDelta - fieldDelta) < 0.001f) {
                    Spore.LOGGER.info("[HealthFieldUtil]  → MATCH via delta!");
                    return field;
                }
            } catch (Exception e) {
                Spore.LOGGER.info("[HealthFieldUtil]  field={} ERR: {}", field.getName(), e.getClass().getSimpleName());
            }
        }
        throw new NoSuchFieldException("Cannot detect health field in LivingEntity");
    }

    /** 标记法探测：写唯一标记 → 调 setHealth → 找被覆写的字段 */
    private static Field detectFieldByMark(LivingEntity entity) {
        List<Field> candidates = new ArrayList<>();
        Map<Field, Float> originals = new HashMap<>();

        for (Field field : LivingEntity.class.getDeclaredFields()) {
            if (field.getType() != float.class) continue;
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                field.setAccessible(true);
                candidates.add(field);
                originals.put(field, field.getFloat(entity));
            } catch (Exception ignored) {}
        }
        if (candidates.size() < 2) return null;

        float markBase = 77777.0f;
        try {
            for (int i = 0; i < candidates.size(); i++) {
                candidates.get(i).setFloat(entity, markBase + i);
            }
            entity.setHealth(markBase + candidates.size());
            for (int i = 0; i < candidates.size(); i++) {
                Field field = candidates.get(i);
                float current = field.getFloat(entity);
                float expected = markBase + i;
                if (Math.abs(current - expected) > 0.5f) {
                    Spore.LOGGER.info("[HealthFieldUtil] mark field={} orig={} → {} ← OVERWRITTEN",
                            field.getName(), originals.get(field), current);
                    return field;
                }
            }
            return null;
        } catch (Exception e) {
            Spore.LOGGER.warn("[HealthFieldUtil] detectFieldByMark error: {}", e.getMessage());
            return null;
        } finally {
            Float restoredHealth = null;
            for (Map.Entry<Field, Float> entry : originals.entrySet()) {
                try {
                    entry.getKey().setFloat(entity, entry.getValue());
                    if (restoredHealth == null) restoredHealth = entry.getValue();
                } catch (Exception ignored) {}
            }
            if (restoredHealth != null) {
                try { entity.setHealth(restoredHealth); } catch (Exception ignored) {}
            }
        }
    }

    // ======== 持续伤害 (Bleed) 系统 ========
    // 每 tick 扣血，叠加式，用于压制高回血目标

    private static final Map<UUID, float[]> BLEEDING = new HashMap<>();

    /**
     * 对目标施加持续伤害。每服务器 tick 扣除 damagePerTick HP，持续 durationTicks tick。
     * 同一目标叠加：damagePerTick 累加，duration 取最大值。
     */
    public static void applyBleed(LivingEntity entity, float damagePerTick, int durationTicks) {
        if (entity == null || damagePerTick <= 0 || durationTicks <= 0) return;
        BLEEDING.merge(entity.getUUID(), new float[]{damagePerTick, durationTicks}, (old, neu) -> {
            old[0] += neu[0];
            old[1] = Math.max(old[1], neu[1]);
            return old;
        });
    }

    /** 每服务器 tick 由 CoreModHooks.tickServer() 调用 */
    public static void tickBleed(MinecraftServer server) {
        if (BLEEDING.isEmpty()) return;
        var it = BLEEDING.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            float[] data = entry.getValue();
            boolean found = false;
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(entry.getKey());
                if (e instanceof LivingEntity living && living.isAlive()) {
                    addHealth(living, -data[0]);
                    found = true;
                    break;
                }
            }
            if (!found) { it.remove(); continue; }
            data[1]--;
            if (data[1] <= 0) it.remove();
        }
    }
}