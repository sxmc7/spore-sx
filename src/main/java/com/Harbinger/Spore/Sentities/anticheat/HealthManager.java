package com.Harbinger.Spore.Sentities.anticheat;

import com.Harbinger.Spore.Sentities.anticheat.AccessChecker;
import com.Harbinger.Spore.Sentities.anticheat.ProtectedWeakHashMap;
import com.Harbinger.Spore.network.PacketHandler;
import com.Harbinger.Spore.network.S2CSyncBossHealth;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public class HealthManager {
    private static final ProtectedWeakHashMap<LivingEntity, Float> healthValues = new ProtectedWeakHashMap();
    // 存储上次健康血量，用于血量异常恢复
    private static final ProtectedWeakHashMap<LivingEntity, Float> lastHealthyHealth = new ProtectedWeakHashMap();
    // 血量异常阈值
    private static final float ANOMALY_THRESHOLD = 0.1f;

    public static void addEntity(LivingEntity entity) {
        healthValues.putIfAbsent(entity, Float.valueOf(entity.getMaxHealth()));
        lastHealthyHealth.putIfAbsent(entity, Float.valueOf(entity.getMaxHealth()));
    }

    public static void removeEntity(LivingEntity entity) {
        healthValues.remove(entity);
        lastHealthyHealth.remove(entity);
    }

    public static void updateHealth(LivingEntity entity, float value) {
        if (AccessChecker.checkAccess()) {
            float clampedValue = Mth.clamp(value, 0.0f, entity.getMaxHealth());
            healthValues.put(entity, Float.valueOf(clampedValue));
            
            // 更新健康血量（只记录正常范围的血量）
            if (clampedValue > 0.0f && clampedValue <= entity.getMaxHealth()) {
                lastHealthyHealth.put(entity, Float.valueOf(clampedValue));
            }
        }
    }

    public static void setHealth(LivingEntity bossEntity, float value) {
        if (AccessChecker.checkAccess() && HealthManager._a(bossEntity) == ((Float)HealthManager.getHealthHashMap().getOrDefault(bossEntity, Float.valueOf(HealthManager._a(bossEntity)))).floatValue()) {
            HealthManager.updateHealth(bossEntity, Mth.clamp(value, 0.0f, bossEntity.getMaxHealth()));
            PacketHandler.sendToClient(new S2CSyncBossHealth(HealthManager._a(bossEntity), bossEntity.getId()));
        }
    }

    public static float _a(LivingEntity bossEntity) {
        try {
            Float health = HealthManager.getHealthHashMap().getOrDefault(bossEntity, Float.valueOf(bossEntity.getMaxHealth()));
            
            // 血量异常检测和恢复机制（学习凋零斯拉）
            if (health != null && health.floatValue() < ANOMALY_THRESHOLD && !bossEntity.isDeadOrDying()) {
                // 血量异常低，但实体未死亡 - 可能是恶意修改
                com.Harbinger.Spore.Spore.LOGGER.warn("[HealthManager] 检测到血量异常: " + bossEntity.getType() + " 血量: " + health);
                
                // 尝试恢复到上次健康血量
                Float lastHealthy = lastHealthyHealth.getOrDefault(bossEntity, Float.valueOf(bossEntity.getMaxHealth()));
                if (lastHealthy != null && lastHealthy.floatValue() > 0.0f) {
                    com.Harbinger.Spore.Spore.LOGGER.info("[HealthManager] 恢复血量到: " + lastHealthy);
                    health = lastHealthy;
                    updateHealth(bossEntity, lastHealthy.floatValue());
                }
            }
            
            return health.floatValue();
        }
        catch (Exception e) {
            return bossEntity.getHealth();
        }
    }

    public static boolean _b(LivingEntity bossEntity) {
        return !bossEntity.isDeadOrDying() && HealthManager._a(bossEntity) > 0.0f;
    }

    public static boolean _c(LivingEntity bossEntity) {
        return HealthManager._a(bossEntity) <= 0.0f;
    }

    public static ProtectedWeakHashMap<LivingEntity, Float> getHealthHashMap() {
        return healthValues;
    }
    
    /**
     * 检查血量是否异常（学习凋零斯拉的血量保护机制）
     */
    public static boolean isHealthAnomalous(LivingEntity entity) {
        try {
            Float health = healthValues.get(entity);
            if (health == null) {
                return false;
            }
            // 检查血量是否异常低但实体未死亡
            return health.floatValue() < ANOMALY_THRESHOLD && !entity.isDeadOrDying();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 强制恢复健康血量
     */
    public static void restoreHealthyHealth(LivingEntity entity) {
        if (AccessChecker.checkAccess()) {
            Float lastHealthy = lastHealthyHealth.get(entity);
            if (lastHealthy != null && lastHealthy.floatValue() > 0.0f) {
                updateHealth(entity, lastHealthy.floatValue());
                com.Harbinger.Spore.Spore.LOGGER.info("[HealthManager] 强制恢复血量: " + entity.getType() + " -> " + lastHealthy);
            }
        }
    }
}