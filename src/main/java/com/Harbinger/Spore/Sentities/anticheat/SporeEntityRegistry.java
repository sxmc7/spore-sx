package com.Harbinger.Spore.Sentities.anticheat;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Spore实体注册器
 * 为所有Spore生物注册到限伤保护系统
 */
@Mod.EventBusSubscriber(modid = "spore")
public class SporeEntityRegistry {

    /**
     * 当实体加入世界时注册到限伤系统
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();

        // 只处理LivingEntity
        if (!(entity instanceof LivingEntity)) {
            return;
        }

        LivingEntity livingEntity = (LivingEntity) entity;

        // 检查是否为Spore生物
        if (isSporeEntity(livingEntity)) {
            // 注册到限伤系统
            if (!DamageLimiter.isRegistered(livingEntity)) {
                DamageLimiter.registerEntity(livingEntity);

                // 为不同类型的生物设置不同的伤害上限
                float damageCap = calculateDamageCap(livingEntity);
                DamageLimiter.setDamageCap(livingEntity, damageCap);
            }
        }
    }

    /**
     * 判断是否为Spore生物
     */
    private static boolean isSporeEntity(LivingEntity entity) {
        // 检查实体类型
        String entityName = entity.getType().toString().toLowerCase();

        // Spore模组的生物通常包含以下关键词
        return entityName.contains("spore") ||
               entityName.contains("infected") ||
               entityName.contains("calamity") ||
               entityName.contains("harbinger") ||
               entity instanceof Infected ||
               entity instanceof Calamity;
    }

    /**
     * 根据生物类型计算伤害上限
     */
    private static float calculateDamageCap(LivingEntity entity) {
        float maxHealth = entity.getMaxHealth();
        String entityName = entity.getType().toString().toLowerCase();

        // Boss生物（更高伤害上限）
        if (entity instanceof Calamity || maxHealth >= 100.0f) {
            return 10.0f;
        }

        // 进化生物（中等伤害上限）
        if (entityName.contains("evolved") || entityName.contains("hyper")) {
            return 7.5f;
        }

        // 普通感染生物（基础伤害上限）
        return 5.0f;
    }

    /**
     * 每帧推进限伤系统（滑动窗口自动过期，无需手动重置）
     */
    public static void tickDamageLimiters() {
        try {
            DamageLimiter.tickAll();
        } catch (Exception e) {
            com.Harbinger.Spore.Spore.LOGGER.warn("Failed to tick damage limiters: " + e.getMessage());
        }
    }
}