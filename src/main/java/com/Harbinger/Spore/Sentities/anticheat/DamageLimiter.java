package com.Harbinger.Spore.Sentities.anticheat;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.Senchantments.ExtremeFrostEnchantment;
import com.Harbinger.Spore.Sitems.BaseWeapons.SporeArmorData;
import com.Harbinger.Spore.Sitems.BaseWeapons.SporeWeaponData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Spore 伤害限制管理器 — 滑动窗口版。
 *
 * 核心改动（相比每 tick 重置版）：
 * 1. 滑动窗口：环形数组 (WINDOW_SIZE=20 tick)，累加窗口内总伤害，不再每 tick 清零。
 *    防止单 tick 内多次攻击爆发绕过。
 * 2. 自适应无敌帧：连续命中会延长无敌窗口（最多翻倍），防止高频攻击刷血。
 * 3. setHealth 独立窗口：hurt 路径和 setHealth 路径各自计窗口，防止通过 setHealth 绕过限伤。
 */
public class DamageLimiter {

    // ======== 滑动窗口参数 ========
    /** 窗口大小（tick）。20 tick = 1 秒 */
    private static final int WINDOW_SIZE = 20;
    /** 窗口内总伤害上限（hurt 路径） */
    private static final float WINDOW_CAP_HURT = 15.0f;
    /** 窗口内总伤害上限（setHealth 路径） */
    private static final float WINDOW_CAP_SETHEALTH = 10.0f;

    // ======== 自适应无敌帧参数 ========
    /** 基础无敌帧（tick） */
    private static final int BASE_INVULN_TICKS = 10;
    /** 最大无敌帧（tick）— 连续命中放大至此 */
    private static final int MAX_INVULN_TICKS = 40;
    /** 连续命中判定窗口（tick 内重复受伤算连续） */
    private static final int CONSECUTIVE_WINDOW = 5;

    // ======== 存储结构 ========

    /** hurt 路径滑动窗口数据 */
    private static final WeakHashMap<LivingEntity, DamageWindow> HURT_WINDOWS = new WeakHashMap<>();
    /** setHealth 路径滑动窗口数据 */
    private static final WeakHashMap<LivingEntity, DamageWindow> SETHEALTH_WINDOWS = new WeakHashMap<>();
    /** 无敌帧数据 */
    private static final WeakHashMap<LivingEntity, InvulnData> INVULN_DATA = new WeakHashMap<>();

    // ======== setHealth 绕过路径无敌帧（独立于 hurt 路径） ========
    private static final WeakHashMap<LivingEntity, InvulnData> BYPASS_INVULN = new WeakHashMap<>();

    /** 检查实体是否处于 setHealth 绕过无敌帧内 */
    public static boolean isBypassInvulnerable(LivingEntity entity) {
        if (entity == null || entity.level() == null) return false;
        InvulnData inv = BYPASS_INVULN.get(entity);
        if (inv == null) return false;
        long tick = entity.level().getGameTime();
        boolean vuln = inv.isInvulnerable(tick);
        if (vuln) log("Bypass无敌帧阻挡 entity=" + entity.getClass().getSimpleName()
            + " invuln=" + inv.currentInvuln + " remain=" + (inv.currentInvuln - (tick - inv.lastHitTick)));
        return vuln;
    }

    /** 记录一次 setHealth 绕过命中，触发自适应无敌帧 */
    public static void recordBypassHit(LivingEntity entity) {
        if (entity == null || entity.level() == null) return;
        InvulnData inv = BYPASS_INVULN.computeIfAbsent(entity, k -> new InvulnData());
        inv.recordHit(entity.level().getGameTime());
        log("Bypass无敌帧记录: entity=" + entity.getClass().getSimpleName()
            + " invuln=" + inv.currentInvuln + " consecutive=" + inv.consecutiveHits);
    }

    /** 实体的伤害上限（可配置） */
    private static final WeakHashMap<LivingEntity, Float> DAMAGE_CAPS = new WeakHashMap<>();

    private static final boolean DEBUG = true;

    // ======== 滑动窗口数据结构 ========

    private static class DamageWindow {
        final float[] buckets = new float[WINDOW_SIZE];
        int cursor = 0;          // 当前 tick 槽位
        long lastTick = -1;      // 上次更新的游戏 tick

        /** 推进到指定 tick（把过期的槽位清零） */
        void advanceTo(long currentTick) {
            if (lastTick < 0) { lastTick = currentTick; return; }
            long elapsed = currentTick - lastTick;
            if (elapsed <= 0) return;
            if (elapsed >= WINDOW_SIZE) {
                // 间隔超过窗口大小 → 全部清零
                for (int i = 0; i < WINDOW_SIZE; i++) buckets[i] = 0f;
                cursor = 0;
            } else {
                for (long i = 0; i < elapsed; i++) {
                    cursor = (cursor + 1) % WINDOW_SIZE;
                    buckets[cursor] = 0f;
                }
            }
            lastTick = currentTick;
        }

        /** 累加伤害到当前槽位 */
        void addDamage(float amount, long currentTick) {
            advanceTo(currentTick);
            buckets[cursor] += amount;
        }

        /** 窗口内总伤害 */
        float total() {
            float sum = 0f;
            for (float v : buckets) sum += v;
            return sum;
        }

        /** 窗口剩余容量 */
        float remaining(float cap) {
            return Math.max(0f, cap - total());
        }
    }

    // ======== 无敌帧数据结构 ========

    private static class InvulnData {
        long lastHitTick = -1;
        int currentInvuln;
        int consecutiveHits = 0;
        final int baseInvuln;
        final int maxInvuln;

        InvulnData() { this.baseInvuln = BASE_INVULN_TICKS; this.maxInvuln = MAX_INVULN_TICKS; this.currentInvuln = baseInvuln; }
        InvulnData(int baseInvuln, int maxInvuln) { this.baseInvuln = baseInvuln; this.maxInvuln = maxInvuln; this.currentInvuln = baseInvuln; }

        boolean isInvulnerable(long currentTick) {
            return lastHitTick >= 0 && (currentTick - lastHitTick) < currentInvuln;
        }

        void recordHit(long currentTick) {
            if (lastHitTick >= 0 && (currentTick - lastHitTick) < CONSECUTIVE_WINDOW) {
                consecutiveHits++;
                currentInvuln = Math.min(maxInvuln, baseInvuln + consecutiveHits * 5);
            } else {
                consecutiveHits = 0;
                currentInvuln = baseInvuln;
            }
            lastHitTick = currentTick;
        }
    }

    // ======== 公开 API ========

    public static void registerEntity(LivingEntity entity) {
        if (!SConfig.SERVER.enable_damage_limit.get()) return;
        if (!AccessChecker.checkAccess()) return;

        float cap = entity.getMaxHealth() >= 100f || entity.hasCustomName()
            ? SConfig.SERVER.boss_damage_limit.get().floatValue()
            : SConfig.SERVER.default_damage_limit.get().floatValue();

        DAMAGE_CAPS.put(entity, cap);
        HURT_WINDOWS.put(entity, new DamageWindow());
        SETHEALTH_WINDOWS.put(entity, new DamageWindow());
        INVULN_DATA.put(entity, new InvulnData());

        log("注册 " + entity.getType() + " cap=" + cap);
    }

    public static void unregisterEntity(LivingEntity entity) {
        HURT_WINDOWS.remove(entity);
        SETHEALTH_WINDOWS.remove(entity);
        INVULN_DATA.remove(entity);
        DAMAGE_CAPS.remove(entity);
    }

    // ======== hurt 路径限伤 ========

    /**
     * hurt 路径限伤（在实体 hurt() 内调用）。
     *
     * 多阶段检查：
     *   0. Spore 护甲双模式
     *   1. 自适应无敌帧
     *   2. 滑动窗口 DPS cap
     *   3. Spore 武器白名单
     */
    public static float limitDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!SConfig.SERVER.enable_damage_limit.get()) return amount;
        if (!AccessChecker.checkAccess() && SConfig.SERVER.enable_damage_limit_anticheat.get()) return amount;

        Entity attacker = source.getEntity();
        long tick = entity.level().getGameTime();

        // 0. Spore 护甲双模式
        if (hasSporeArmor(entity) && attacker instanceof LivingEntity la) {
            if (!la.getClass().getName().startsWith("com.Harbinger.Spore.")) {
                float sporeCap = Math.max(1f, entity.getMaxHealth() * 0.01f);
                float remaining = getOrCreateWindow(SETHEALTH_WINDOWS, entity, tick).remaining(sporeCap);
                log("Spore护甲限伤 cap=" + sporeCap + " remaining=" + remaining);
                return Math.min(amount, remaining);
            }
        }

        // Spore 武器 / 极寒附魔 — 跳过白名单后全部限伤
        if (attacker instanceof LivingEntity la) {
            if (ExtremeFrostEnchantment.hasExtremeFrost(la)) {
                float mult = ExtremeFrostEnchantment.getDamageMultiplier(ExtremeFrostEnchantment.getExtremeFrostLevel(la));
                log("极寒白名单 mult=" + mult);
                return amount * mult;
            }
            if (!la.getMainHandItem().isEmpty() && la.getMainHandItem().getItem() instanceof SporeWeaponData) {
                log("Spore武器白名单");
                return amount;
            }
        }

        // 1. 自适应无敌帧
        InvulnData inv = INVULN_DATA.computeIfAbsent(entity, k -> new InvulnData());
        if (inv.isInvulnerable(tick)) {
            log("无敌帧阻挡 tick=" + tick + " lastHit=" + inv.lastHitTick + " invuln=" + inv.currentInvuln);
            return 0f;
        }

        // 2. 滑动窗口 DPS cap
        DamageWindow window = getOrCreateWindow(HURT_WINDOWS, entity, tick);
        float cap = DAMAGE_CAPS.getOrDefault(entity, SConfig.SERVER.default_damage_limit.get().floatValue());
        float remaining = window.remaining(cap);

        if (remaining <= 0f) {
            log("窗口超限 cap=" + cap + " total=" + window.total());
            return 0f;
        }

        float allowed = Math.min(amount, remaining);
        window.addDamage(allowed, tick);
        inv.recordHit(tick);

        log("允许伤害 " + allowed + "/" + amount + " 窗口=" + window.total() + "/" + cap + " window");
        return allowed;
    }

    // ======== setHealth 路径限伤 ========

    /**
     * setHealth 路径滑动窗口限伤。
     * 与 hurt 路径使用独立的窗口计数器，防止通过 setHealth 绕过 DPS cap。
     *
     * @return 允许的扣减量（<= requestedReduction，可能为 0）
     */
    public static float applyFrameCap(LivingEntity entity, float requestedReduction) {
        if (!SConfig.SERVER.enable_damage_limit.get()) return requestedReduction;
        if (requestedReduction <= 0f) return 0f;
        if (!AccessChecker.checkAccess() && SConfig.SERVER.enable_damage_limit_anticheat.get()) {
            return requestedReduction;
        }

        long tick = entity.level().getGameTime();
        DamageWindow window = getOrCreateWindow(SETHEALTH_WINDOWS, entity, tick);
        float remaining = window.remaining(WINDOW_CAP_SETHEALTH);

        if (remaining <= 0f) {
            log("setHealth 窗口超限 total=" + window.total());
            return 0f;
        }

        float allowed = Math.min(requestedReduction, remaining);
        window.addDamage(allowed, tick);
        log("setHealth 允许 " + allowed + "/" + requestedReduction + " 窗口=" + window.total() + "/" + WINDOW_CAP_SETHEALTH);
        return allowed;
    }

    // ======== Tick 推进 ========

    /**
     * 每 tick 调用。不再重置伤害（滑动窗口自动过期），
     * 仅做窗口数据的基本维护。
     */
    public static void tickAll() {
        // 滑动窗口自动过期，无需重置。
        // 此处可用于清理已死亡实体的数据（WeakHashMap 自动处理）
    }

    // ======== 辅助方法 ========

    private static DamageWindow getOrCreateWindow(WeakHashMap<LivingEntity, DamageWindow> map, LivingEntity entity, long tick) {
        return map.computeIfAbsent(entity, k -> {
            DamageWindow w = new DamageWindow();
            w.lastTick = tick;
            return w;
        });
    }

    private static boolean hasSporeArmor(LivingEntity entity) {
        if (entity == null) return false;
        for (net.minecraft.world.item.ItemStack slot : entity.getArmorSlots()) {
            if (slot.getItem() instanceof SporeArmorData) return true;
        }
        return false;
    }

    private static void log(String msg) {
        if (DEBUG) com.Harbinger.Spore.Spore.LOGGER.info("[限伤] " + msg);
    }

    // ======== Spore盔甲限伤系统 ========
    /** 非全套：固定无敌帧 20 tick */
    private static final int ARMOR_INVULN_TICKS = 20;
    private static final WeakHashMap<LivingEntity, Long> ARMOR_LAST_HIT = new WeakHashMap<>();

    /** 全套：自适应无敌帧 (20-40 tick, 起始=原有固定值, 最大=实体级) */
    private static final int ARMOR_BASE_INVULN = 20;
    private static final int ARMOR_MAX_INVULN = 40;
    private static final WeakHashMap<LivingEntity, InvulnData> ARMOR_ADAPTIVE_INVULN = new WeakHashMap<>();

    public static boolean isArmorInvulnerable(LivingEntity entity) {
        if (entity == null || entity.level() == null) return false;
        long tick = entity.level().getGameTime();
        if (hasFullSporeArmorSet(entity)) {
            InvulnData inv = ARMOR_ADAPTIVE_INVULN.get(entity);
            if (inv == null) return false;
            return inv.isInvulnerable(tick);
        }
        Long lastHit = ARMOR_LAST_HIT.get(entity);
        if (lastHit == null) return false;
        return (tick - lastHit) < ARMOR_INVULN_TICKS;
    }

    public static void recordArmorHit(LivingEntity entity) {
        if (entity == null || entity.level() == null) return;
        if (hasFullSporeArmorSet(entity)) {
            InvulnData inv = ARMOR_ADAPTIVE_INVULN.computeIfAbsent(entity, k -> new InvulnData(ARMOR_BASE_INVULN, ARMOR_MAX_INVULN));
            inv.recordHit(entity.level().getGameTime());
        } else {
            ARMOR_LAST_HIT.put(entity, entity.level().getGameTime());
        }
    }

    /** Spore盔甲限伤（非Spore攻击者）：固定1点伤害，1秒窗口 */
    public static float limitArmorDamage(LivingEntity entity, float amount) {
        return Math.min(amount, 1f);
    }

    /** 公开检查目标是否穿着任何 Spore 盔甲 */
    public static boolean targetHasSporeArmor(LivingEntity entity) {
        return hasSporeArmor(entity);
    }

    /** 检查目标是否穿着全套（4件）Spore 盔甲 */
    public static boolean hasFullSporeArmorSet(LivingEntity entity) {
        if (entity == null) return false;
        int count = 0;
        for (net.minecraft.world.item.ItemStack slot : entity.getArmorSlots()) {
            if (slot.getItem() instanceof SporeArmorData) count++;
        }
        return count >= 4;
    }

    /** 旧版接口 — 检查无敌帧（委托给自适应无敌帧系统） */
    public static boolean isInInvincibilityFrame(LivingEntity entity) {
        InvulnData inv = INVULN_DATA.get(entity);
        if (inv == null) return false;
        long tick = entity.level().getGameTime();
        return inv.lastHitTick >= 0 && (tick - inv.lastHitTick) < 5;
    }

    /** 旧版接口 — 记录受伤时间 */
    public static void recordDamageTime(LivingEntity entity) {
        InvulnData inv = INVULN_DATA.computeIfAbsent(entity, k -> new InvulnData());
        inv.lastHitTick = entity.level().getGameTime();
    }

    /** 调试命令用 — 获取系统状态 */
    public static String getSystemStatus() {
        return "=== 限伤系统(滑动窗口) ===\n"
            + "启用: " + SConfig.SERVER.enable_damage_limit.get() + "\n"
            + "窗口大小: " + WINDOW_SIZE + " tick\n"
            + "hurt上限: " + WINDOW_CAP_HURT + " | setHealth上限: " + WINDOW_CAP_SETHEALTH + "\n"
            + "已注册: " + HURT_WINDOWS.size() + " 实体\n";
    }

    // ======== 兼容旧接口（EntityUtil 等使用） ========

    public static boolean isRegistered(LivingEntity entity) { return HURT_WINDOWS.containsKey(entity); }
    public static float getDamageCap(LivingEntity entity) {
        return DAMAGE_CAPS.getOrDefault(entity, SConfig.SERVER.default_damage_limit.get().floatValue());
    }
    public static void setDamageCap(LivingEntity entity, float cap) {
        DAMAGE_CAPS.put(entity, Mth.clamp(cap, 0f, Float.POSITIVE_INFINITY));
    }
    public static float getCurrentDamage(LivingEntity entity) {
        DamageWindow w = HURT_WINDOWS.get(entity);
        return w != null ? w.total() : 0f;
    }
    // 旧版接口—空实现
    public static void resetDamageHistory(LivingEntity entity) {}
    public static void resetAllDamageHistory() {}
    public static boolean hasDamageCapacity(LivingEntity entity, float amount) {
        return applyFrameCap(entity, amount) >= amount;
    }
}
