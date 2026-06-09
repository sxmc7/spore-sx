package com.Harbinger.Spore.Sentities.util;

import com.Harbinger.Spore.Spore;
import com.Harbinger.Spore.util.UnsafeHealthHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 实体实际伤害工具
 * 绕过无敌帧检查，直接修改血量
 * 使用 HealthFieldUtil（AUTHORIZED 标记绕过 Mixin/CoreMod 限伤）
 */
public class EntityActuallyHurtUtil {

    /**
     * 直接对实体造成伤害，绕过无敌帧检查
     */
    public static void actuallyHurt(LivingEntity entity, DamageSource source, float amount) {
        try {
            if (entity.level().isClientSide || entity.isDeadOrDying()) {
                return;
            }

            // 读当前血量，计算新血量 — 用 UnsafeHealthHelper 直写（比 HealthFieldUtil 多 Unsafe 层）
            float currentHealth = UnsafeHealthHelper.getHealth(entity);
            float newHealth = Math.max(currentHealth - amount, 0.0f);

            // 用 UnsafeHealthHelper 绕过限伤直写（Capability → Unsafe DataItem → HealthFieldUtil → Delta 四层）
            UnsafeHealthHelper.setHealth(entity, newHealth);

            // 重置无敌帧，允许连续帧伤
            entity.invulnerableTime = 0;

            Spore.LOGGER.debug("[ActuallyHurt] 对 {} 造成 {} 点伤害（绕过无敌帧）", entity.getType(), amount);

        } catch (Exception e) {
            Spore.LOGGER.error("[ActuallyHurt] 伤害失败: {}", e.getMessage());
            entity.hurt(source, amount);
        }
    }

    /**
     * 检查实体是否可以受到伤害（忽略无敌帧）
     */
    public static boolean canHurt(LivingEntity entity) {
        return entity.isAlive() && !entity.isDeadOrDying() && !entity.level().isClientSide;
    }

    /**
     * 对实体造成无限伤害（瞬杀）
     */
    public static void instantKill(LivingEntity entity, DamageSource source) {
        actuallyHurt(entity, source, Float.POSITIVE_INFINITY);
    }

    /**
     * 对实体造成负无穷伤害（强制清除）
     */
    public static void voidKill(LivingEntity entity, DamageSource source) {
        actuallyHurt(entity, source, Float.NEGATIVE_INFINITY);
    }
}