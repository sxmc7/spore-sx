package com.Harbinger.Spore.Sentities.anticheat;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.Spore;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;

/**
 * 增强版异常回血系统 - 完全防御炼狱结界
 * 借鉴KubeJS的monitorHealth机制，每帧检查血量变化
 * 特点：
 * 1. 每帧监控血量变化（无延迟）
 * 2. 检测到异常立即回滚
 * 3. 持续自动回血
 * 4. 防御所有绕过hurt()的攻击
 */
public class HealthRegainSystem {
    // 使用ProtectedWeakHashMap防止作弊
    private static final ProtectedWeakHashMap<LivingEntity, Float> currentTickHealth = new ProtectedWeakHashMap<>();
    private static final ProtectedWeakHashMap<LivingEntity, Float> previousTickHealth = new ProtectedWeakHashMap<>();
    private static final ProtectedWeakHashMap<LivingEntity, Boolean> enabledEntities = new ProtectedWeakHashMap<>();
    private static final ProtectedWeakHashMap<LivingEntity, Integer> tickCounter = new ProtectedWeakHashMap<>();
    
    // 异常伤害阈值：最大血量的1%（对标KubeJS的5%）
    private static final float ABNORMAL_DAMAGE_THRESHOLD = 0.01f; // 1%
    // 最小伤害阈值：至少0.5点伤害才触发
    private static final float MIN_DAMAGE_THRESHOLD = 0.5f;
    // 每帧自动回血量：最大血量的0.5%
    private static final float AUTO_HEAL_PER_TICK = 0.005f; // 0.5%
    
    /**
     * 注册实体到异常回血系统
     */
    public static void registerEntity(LivingEntity entity) {
        if (!SConfig.SERVER.enable_damage_limit.get()) {
            return;
        }
        
        if (AccessChecker.checkAccess()) {
            enabledEntities.put(entity, Boolean.TRUE);
            currentTickHealth.put(entity, Float.valueOf(entity.getHealth()));
            previousTickHealth.put(entity, Float.valueOf(entity.getHealth()));
            tickCounter.put(entity, Integer.valueOf(0));
            Spore.LOGGER.info("[异常回血] 实体已注册: " + entity.getType() + " 初始血量: " + entity.getHealth());
        }
    }
    
    /**
     * 从异常回血系统中移除实体
     */
    public static void unregisterEntity(LivingEntity entity) {
        if (AccessChecker.checkAccess()) {
            enabledEntities.remove(entity);
            currentTickHealth.remove(entity);
            previousTickHealth.remove(entity);
            tickCounter.remove(entity);
            Spore.LOGGER.info("[异常回血] 实体已移除: " + entity.getType());
        }
    }
    
    /**
     * 每帧监控血量变化（对标KubeJS的monitorHealth）
     * 这个方法应该在tick()方法的开始和结束都调用
     */
    public static void monitorHealth(LivingEntity entity) {
        // 第一行就记录调用
        String entityType = entity.getType().toString();
        boolean enabled = enabledEntities.containsKey(entity);
        boolean deadOrDying = entity.isDeadOrDying();
        boolean clientSide = entity.level().isClientSide;
        
        if (!enabled || deadOrDying || clientSide) {
            // 记录为什么不执行
            if (!enabled) {
                Spore.LOGGER.debug("[异常回血] monitorHealth返回: " + entityType + " 未注册到enabledEntities");
            } else if (deadOrDying) {
                Spore.LOGGER.debug("[异常回血] monitorHealth返回: " + entityType + " 已死亡");
            } else if (clientSide) {
                // 客户端不记录日志
            }
            return;
        }
        
        // 移除AccessChecker检查，让系统无条件执行（防御所有模组的攻击）
        // 原问题：AccessChecker只允许特定包调用，炼狱结界来自其他模组会被拒绝
        
        float currentHealth = entity.getHealth();
        float maxHealth = entity.getMaxHealth();
        
        Float previousHealth = previousTickHealth.get(entity);
        if (previousHealth == null) {
            previousTickHealth.put(entity, Float.valueOf(currentHealth));
            Spore.LOGGER.info("[异常回血] 首次记录血量: " + entity.getType() + " 当前血量: " + currentHealth + "/" + maxHealth);
            return;
        }
        
        // 计算血量变化
        float healthChange = previousHealth.floatValue() - currentHealth;
        
        // 每帧记录血量变化（调试用）
        if (healthChange != 0) {
            Spore.LOGGER.info("[异常回血] 血量变化: " + entity.getType() + 
                " 上帧: " + previousHealth.floatValue() + " 当前: " + currentHealth + 
                " 变化: " + healthChange + " (正数=减少)");
        }
        
        // 计算阈值
        float abnormalThreshold = Math.max(maxHealth * ABNORMAL_DAMAGE_THRESHOLD, MIN_DAMAGE_THRESHOLD);
        
        // 检测异常血量减少
        if (healthChange > abnormalThreshold) {
            Spore.LOGGER.info("[异常回血] 检测到异常血量减少: " + entity.getType() + 
                " 减少: " + healthChange + " (阈值: " + abnormalThreshold + ") 回滚血量");
            
            // 立即回滚血量
            entity.setHealth(previousHealth.floatValue());
            
            // 重置计数器
            tickCounter.put(entity, Integer.valueOf(0));
        } else if (healthChange > 0) {
            // 正常伤害，更新前一次血量
            previousTickHealth.put(entity, Float.valueOf(currentHealth));
            
            // 累积伤害计数
            Integer counter = tickCounter.get(entity);
            if (counter != null) {
                tickCounter.put(entity, Integer.valueOf(counter.intValue() + 1));
            }
        } else {
            // 血量增加或不变，重置计数器
            tickCounter.put(entity, Integer.valueOf(0));
            previousTickHealth.put(entity, Float.valueOf(currentHealth));
        }
        
        // 持续自动回血（每帧恢复0.01%最大血量，降低回血量避免抵消炼狱结界伤害）
        // 原问题：0.5%回血太快，完全抵消炼狱结界的0.05伤害，导致检测不到异常
        if (currentHealth < maxHealth && currentHealth > 0) {
            float healAmount = maxHealth * 0.0001f; // 从0.5%降低到0.01%
            float newHealth = Math.min(maxHealth, currentHealth + healAmount);
            entity.setHealth(newHealth);
        }
        
        // 更新当前帧血量
        currentTickHealth.put(entity, Float.valueOf(entity.getHealth()));
    }
    
    /**
     * 检查实体是否已注册
     */
    public static boolean isRegistered(LivingEntity entity) {
        return enabledEntities.containsKey(entity);
    }
    
    /**
     * 获取系统状态信息
     */
    public static String getSystemStatus() {
        StringBuilder status = new StringBuilder();
        status.append("=== 异常回血系统状态 ===\n");
        status.append("启用状态: ").append(SConfig.SERVER.enable_damage_limit.get()).append("\n");
        status.append("异常伤害阈值: ").append(ABNORMAL_DAMAGE_THRESHOLD * 100).append("%\n");
        status.append("自动回血: ").append(AUTO_HEAL_PER_TICK * 100).append("%/tick\n");
        status.append("已注册实体数: ").append(enabledEntities.size()).append("\n");
        return status.toString();
    }
    
    /**
     * 立即回滚血量到上一帧（用于紧急情况）
     */
    public static void emergencyRollback(LivingEntity entity) {
        if (!enabledEntities.containsKey(entity)) {
            return;
        }
        
        Float previousHealth = previousTickHealth.get(entity);
        if (previousHealth != null) {
            entity.setHealth(previousHealth.floatValue());
            Spore.LOGGER.warn("[异常回血] 紧急回滚: " + entity.getType() + " 血量恢复到: " + previousHealth.floatValue());
        }
    }
}