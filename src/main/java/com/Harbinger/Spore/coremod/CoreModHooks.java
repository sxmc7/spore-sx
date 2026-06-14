package com.Harbinger.Spore.coremod;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.Sentities.anticheat.DamageLimiter;
import com.Harbinger.Spore.Sentities.anticheat.StackChecker;
import com.Harbinger.Spore.Sentities.anticheat.SporeEntityRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityAccess;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CoreModHooks {
    // ======== 真实无敌帧系统 ========
    // 字节码级硬闸: 在 hurt() 最开头检查，无条件阻挡伤害
    // Spore 武器/生物通过 clearRealInvuln() 绕过
    private static final int REAL_INVULN_TICKS = 4; // 0.2 秒

    /**
     * 检查实体是否处于真实无敌帧（字节码级硬闸）。
     * 在 LivingEntity.hurt() 最开头通过 ASM 注入调用。
     * @return true = 无条件阻挡本次伤害
     */
    public static boolean checkRealInvuln(LivingEntity entity) {
        if (entity == null) return false;
        if (entity.level() == null) return false; // 世界未加载时放行
        net.minecraft.nbt.CompoundTag tag = entity.getPersistentData();
        if (tag.contains("spore_real_invuln")) {
            if (entity.level().getGameTime() < tag.getLong("spore_real_invuln")) {
                com.Harbinger.Spore.Spore.LOGGER.info("[Invuln] 阻挡伤害: entity={} 剩余时间={}",
                        entity.getClass().getSimpleName(),
                        tag.getLong("spore_real_invuln") - entity.level().getGameTime());
                return true; // 仍处于无敌帧，阻挡伤害
            }
            tag.remove("spore_real_invuln"); // 过期清理
        }
        return false;
    }

    /**
     * 清除真实无敌帧。Spore 武器/生物在造成伤害前调用。
     * 同时重置原版 invulnerableTime 以确保本次伤害生效。
     */
    public static void clearRealInvuln(LivingEntity entity) {
        if (entity == null) return;
        entity.getPersistentData().remove("spore_real_invuln");
        entity.invulnerableTime = 0;
    }

    /**
     * hurt() 返回时调用。如果伤害实际生效（result==true），
     * 设置真实无敌帧 NBT。
     */
    public static void onHurtReturn(LivingEntity entity, boolean result) {
        if (entity == null || !result) return;
        if (entity.level() == null) return;
        entity.getPersistentData().putLong("spore_real_invuln",
                entity.level().getGameTime() + REAL_INVULN_TICKS);
        com.Harbinger.Spore.Spore.LOGGER.info("[Invuln] 设置无敌帧: entity={} expires={}",
                entity.getClass().getSimpleName(),
                entity.level().getGameTime() + REAL_INVULN_TICKS);
    }

    /**
     * AOE 真伤钩子 — 委托给 HealthFieldUtil（VarHandle/反射/原版 三级回退）
     *
     * 不受 bytecode limitSetHealth 限伤，不受其他 Mod Mixin 拦截。
     * 极寒附魔持有者应在调用前过滤。
     */
    public static void applyTrueDamage(LivingEntity target, float amount) {
        if (target == null || !target.isAlive() || amount <= 0) return;
        com.Harbinger.Spore.util.UnsafeHealthHelper.addHealth(target, -amount);
    }
    public static void renderAfterEntities(PoseStack poseStack, Camera camera) {
        // Spore模组的渲染钩子
    }

    // ======== 血量影子备份系统 ========
    // 用于每 tick 一致性校验，检测 Unsafe 直写 DataItem.value 或其他绕过 setHealth 的篡改
    private static final Map<UUID, HealthShadow> HEALTH_BACKUPS = new ConcurrentHashMap<>();
    private static final float TAMPER_THRESHOLD = 0.5f; // 允许偏差阈值（超过即视为篡改）

    private static class HealthShadow {
        float expectedHealth;
        long tick;
        HealthShadow(float health, long tick) { this.expectedHealth = health; this.tick = tick; }
    }

    /** 记录血量影子备份。在 limitSetHealth 扣血通过后调用。 */
    private static void recordHealthBackup(LivingEntity entity, float health) {
        if (entity == null || entity.level() == null) return;
        HEALTH_BACKUPS.put(entity.getUUID(), new HealthShadow(health, entity.level().getGameTime()));
    }

    /**
     * 每 tick 血量一致性校验。
     * 检测对象：Unsafe 直写 SynchedEntityData DataItem.value、外部 mod 反射改血等。
     * 发现不一致时用影子值覆盖恢复。
     */
    private static void checkHealthConsistency(net.minecraft.server.MinecraftServer server) {
        if (HEALTH_BACKUPS.isEmpty() || server == null) return;
        long currentTick = server.getTickCount();
        var it = HEALTH_BACKUPS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID uuid = entry.getKey();
            HealthShadow shadow = entry.getValue();
            // 影子备份超过 200 tick（10秒）未更新 → 视为过期，清理
            if (currentTick - shadow.tick > 200) { it.remove(); continue; }
            boolean found = false;
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(uuid);
                if (e instanceof LivingEntity living && living.isAlive()) {
                    found = true;
                    float actual = living.getHealth();
                    if (Math.abs(actual - shadow.expectedHealth) > TAMPER_THRESHOLD) {
                        com.Harbinger.Spore.Spore.LOGGER.warn(
                            "[HealthCheck] 检测到血量篡改! entity={} expected={} actual={}",
                            living.getClass().getSimpleName(), shadow.expectedHealth, actual);
                        com.Harbinger.Spore.util.HealthFieldUtil.setHealth(living, shadow.expectedHealth);
                    }
                    break;
                }
            }
            if (!found) it.remove();
        }
    }

    public static void tickServer() {
        try {
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;

            // 1. 推进限伤系统（滑动窗口自动过期）
            try {
                SporeEntityRegistry.tickDamageLimiters();
            } catch (Exception e) {
                com.Harbinger.Spore.Spore.LOGGER.warn("Failed to tick damage limiters: " + e.getMessage());
            }

            // 2. 血量一致性校验（防御 Unsafe 直写 DataItem.value）
            try {
                checkHealthConsistency(server);
            } catch (Exception e) {
                com.Harbinger.Spore.Spore.LOGGER.warn("Failed health consistency check: " + e.getMessage());
            }

            // 3. 持续伤害 (Bleed)
            try {
                com.Harbinger.Spore.util.HealthFieldUtil.tickBleed(server);
            } catch (Exception e) {
                com.Harbinger.Spore.Spore.LOGGER.warn("Failed to tick bleed: " + e.getMessage());
            }
        } catch (Exception e) {
            com.Harbinger.Spore.Spore.LOGGER.warn("Failed to tick server: " + e.getMessage());
        }
    }

    public static boolean checkDespawnImmunity(Entity entity) {
        if (!StackChecker.notCalledFromSporeOrMinecraft(1, 10, true)) {
            return false;
        }
        // Spore生物的反生成免疫检查
        return false;
    }

    public static <T extends EntityAccess> boolean checkEntitySpawning(T entity) {
        if (entity instanceof Entity) {
            Entity entity1 = (Entity)entity;
            // Spore生物的生成控制
            return true;
        }
        return true;
    }

    public static boolean isTimeStopped(LivingEntity entity) {
        CompoundTag tag = entity.getPersistentData();
        if (tag.contains("spore_freeze_until")) {
            if (entity.level().getGameTime() < tag.getLong("spore_freeze_until")) {
                return true; // 时停中，跳过 travel / serverAiStep
            }
            tag.remove("spore_freeze_until"); // 过期清理
        }
        return false;
    }

    public static float limitSetHealth(LivingEntity entity, float newHealth) {
        // Layer 0: HealthFieldUtil 授权写入 — 无条件放行
        if (com.Harbinger.Spore.util.HealthFieldUtil.isAuthorized()) {
            recordHealthBackup(entity, newHealth);
            return newHealth;
        }

        float currentHealth = entity.getHealth();

        // Layer 1: 真实无敌帧保护 — 阻挡扣血
        if (isInRealInvuln(entity) && newHealth < currentHealth) {
            recordHealthBackup(entity, currentHealth);
            return currentHealth;
        }

        boolean isSpore = entity.getClass().getName().startsWith("com.Harbinger.Spore.");
        boolean isArmorProtected = !isSpore && DamageLimiter.hasFullSporeArmorSet(entity);

        if ((!isSpore && !isArmorProtected) || newHealth >= currentHealth) {
            if (isSpore || isArmorProtected) recordHealthBackup(entity, newHealth);
            return newHealth;
        }

        // Layer 2: Spore 实体每次 setHealth 最多降低 1% 当前血量
        float rawReduction = currentHealth - newHealth;
        float maxReduction = Math.max(currentHealth * 0.01f, 0.5f);
        if (rawReduction > maxReduction) {
            newHealth = currentHealth - maxReduction;
        }

        // Layer 3: DamageLimiter 帧伤上限（扩展至 setHealth 路径，与 hurt 路径共享计数器）
        float reductionAfterL2 = currentHealth - newHealth;
        float allowed = DamageLimiter.applyFrameCap(entity, reductionAfterL2);
        if (allowed < reductionAfterL2) {
            newHealth = currentHealth - allowed;
        }

        recordHealthBackup(entity, newHealth);
        return newHealth;
    }

    /** 检查实体是否处于真实无敌帧（供 limitSetHealth 内部使用） */
    private static boolean isInRealInvuln(LivingEntity entity) {
        if (entity == null) return false;
        if (entity.level() == null) return false;
        net.minecraft.nbt.CompoundTag tag = entity.getPersistentData();
        if (tag.contains("spore_real_invuln")) {
            if (entity.level().getGameTime() < tag.getLong("spore_real_invuln")) {
                return true;
            }
            tag.remove("spore_real_invuln");
        }
        return false;
    }

    public static boolean shouldBlockHeal(LivingEntity entity) {
        // Spore entities are immune to anti-heal at the bytecode level
        if (entity.getClass().getName().startsWith("com.Harbinger.Spore.")) {
            CompoundTag tag = entity.getPersistentData();
            if (tag.contains("spore_frost_antiheal")) {
                tag.remove("spore_frost_antiheal");
                tag.remove("spore_frost_antiheal_time");
            }
            return false;
        }
        // 全套 Spore 装备：免疫反治疗
        if (DamageLimiter.hasFullSporeArmorSet(entity)) {
            CompoundTag tag2 = entity.getPersistentData();
            if (tag2.contains("spore_frost_antiheal")) {
                tag2.remove("spore_frost_antiheal");
                tag2.remove("spore_frost_antiheal_time");
            }
            return false;
        }
        // Non-Spore: check anti-heal flag set by ExtremeFrost enchantment or Spore weapon true damage
        CompoundTag tag = entity.getPersistentData();
        if (tag.contains("spore_frost_antiheal")) {
            if (entity.level().getGameTime() < tag.getLong("spore_frost_antiheal_time")) {
                return true;  // Block healing
            }
            // Expired — clean up stale flags
            tag.remove("spore_frost_antiheal");
            tag.remove("spore_frost_antiheal_time");
        }
        return false;
    }

    /**
     * getHealth() ASM 钩子 — 在每个 FRETURN 前调用。
     * 当 setHealth(0) 或直写导致 getHealth() 返回 0 时，阻止进入 tickDeath。
     * 仅对全套 Spore 装备生效。
     */
    public static float guardGetHealth(LivingEntity entity, float value) {
        if (value > 0) return value;
        if (entity == null) return value;
        try {
            boolean hasArmor = DamageLimiter.hasFullSporeArmorSet(entity);
            if (hasArmor) {
                float maxHealth = entity.getMaxHealth();
                float safe = maxHealth > 0 ? maxHealth : 20.0f;
                com.Harbinger.Spore.Spore.LOGGER.info(
                    "[GetHealth] 拦截零值! entity={} 原值={} 返回={}",
                    entity.getClass().getSimpleName(), value, safe);
                return safe;
            } else {
                com.Harbinger.Spore.Spore.LOGGER.debug(
                    "[GetHealth] 零值但无装备: entity={} value={}",
                    entity.getClass().getSimpleName(), value);
            }
        } catch (Exception e) {
            com.Harbinger.Spore.Spore.LOGGER.error("[GetHealth] 异常: " + e.getMessage());
        }
        return value;
    }
}