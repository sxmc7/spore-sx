package com.Harbinger.Spore.Sentities.anticheat;

import net.minecraft.world.entity.LivingEntity;

/**
 * Spore Boss生物接口 - 对标omnimobs的BossEntity
 * 包含限伤、抗性、百分比伤害等核心机制
 */
public interface SporeBossEntity {
    void registerBossHealth();
    void unregisterBossHealth();
    LivingEntity getBossEntity();
    
    /**
     * 获取伤害上限 - 每帧最大承受伤害
     * @return 伤害上限值，默认为最大血量的1/20
     */
    float getDamageCap();
    
    /**
     * 获取抗性 - 伤害减少系数
     * @return 抗性值，1.0f表示正常，2.0f表示伤害减半
     */
    float getResistance();
    
    /**
     * 获取弱伤害限制 - 忽略的伤害阈值
     * @return 弱伤害限制值，低于此值的伤害被忽略
     */
    float getWeakDamageLimit();
    
    /**
     * 计算百分比伤害
     * @param target 目标生物
     * @param baseDamage 基础伤害
     * @param percentage 百分比(0-100)
     * @return 最终伤害值
     */
    float calculatePercentDamage(LivingEntity target, float baseDamage, float percentage);
}