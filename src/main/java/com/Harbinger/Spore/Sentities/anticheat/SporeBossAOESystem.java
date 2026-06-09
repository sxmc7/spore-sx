package com.Harbinger.Spore.Sentities.anticheat;

import com.Harbinger.Spore.Spore;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Random;
import java.util.WeakHashMap;

/**
 * Spore Boss AOE系统
 * 借鉴炼狱结界的机制：
 * 1. 持续范围伤害（一直存在，直到禁用）
 * 2. 30+种不同伤害源（不完全照抄，但包含多种类型）
 * 3. 绕过护甲
 * 4. 重置目标无敌帧
 * 5. 基于最大血量的百分比伤害
 */
public class SporeBossAOESystem {
    private static final Random random = new Random();
    
    // Boss AOE数据
    private static final WeakHashMap<LivingEntity, AOEData> aoeData = new WeakHashMap<>();
    
    /**
     * AOE数据
     */
    public static class AOEData {
        public boolean enabled;
        public float damagePercent; // 百分比伤害（最大血量的百分比）
        public float range;
        public int tickCount;
        public int attackCount;
        
        public AOEData(float damagePercent, float range) {
            this.enabled = true;
            this.damagePercent = damagePercent;
            this.range = range;
            this.tickCount = 0;
            this.attackCount = 0;
        }
    }
    
    /**
     * 启用Boss AOE伤害（类似炼狱结界）
     */
    public static void enableAOE(LivingEntity boss, float damagePercent, float range) {
        aoeData.put(boss, new AOEData(damagePercent, range));
        Spore.LOGGER.info("[Boss AOE] " + boss.getType() + " 启用炼狱结界风格AOE: " + damagePercent + "%伤害 范围: " + range);
    }
    
    /**
     * 禁用Boss AOE伤害
     */
    public static void disableAOE(LivingEntity boss) {
        aoeData.remove(boss);
        Spore.LOGGER.info("[Boss AOE] " + boss.getType() + " 禁用AOE伤害");
    }
    
    /**
     * 检查实体是否是Spore生物（白名单）
     * 包括：Infected、EvolvedInfected、Organoid、Calamity、Hyper、Experiment、UtilityEntity
     */
    private static boolean isSporeEntity(LivingEntity entity) {
        return entity instanceof com.Harbinger.Spore.Sentities.BaseEntities.Infected
            || entity instanceof com.Harbinger.Spore.Sentities.BaseEntities.EvolvedInfected
            || entity instanceof com.Harbinger.Spore.Sentities.BaseEntities.Organoid
            || entity instanceof com.Harbinger.Spore.Sentities.BaseEntities.Calamity
            || entity instanceof com.Harbinger.Spore.Sentities.BaseEntities.Hyper
            || entity instanceof com.Harbinger.Spore.Sentities.BaseEntities.Experiment
            || entity instanceof com.Harbinger.Spore.Sentities.BaseEntities.UtilityEntity;
    }
    
    /**
     * 更新AOE伤害（每帧调用）
     * 借鉴炼狱结界的perform()方法
     * 包含：普通伤害、setMaxHealth(0)攻击、hurt调用攻击
     * 增强版：绕过护甲、禁止回血、冻结位置
     */
    public static void updateAOE(LivingEntity boss) {
        AOEData data = aoeData.get(boss);
        if (data == null || !data.enabled || boss.level().isClientSide || !boss.isAlive()) {
            return;
        }
        
        Level level = boss.level();
        AABB aabb = boss.getBoundingBox().inflate(data.range);
        
        // 获取范围内的目标（类似炼狱结界）
        // 排除：Boss自己、创造模式玩家、Spore生物（白名单）、手持极寒附魔武器的目标
        java.util.List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, aabb, 
            entity -> entity != boss 
                    && entity.isAlive() 
                    && !(entity instanceof Player player && player.isCreative())
                    && !isSporeEntity(entity)
                    // 极寒附魔：手持时无视 AOE
                    && !com.Harbinger.Spore.Senchantments.ExtremeFrostEnchantment.shouldIgnoreAOE(entity));
        
        // 对每个目标进行多连击伤害（借鉴炼狱结界的多伤害源机制）
        for (LivingEntity target : targets) {
            // 每帧10%概率触发setMaxHealth(0)+真伤攻击（极寒免疫）
            if (random.nextFloat() < 0.1f) {
                var maxAttr = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (maxAttr != null) {
                    maxAttr.setBaseValue(0.0);          // 基础值归零
                    maxAttr.removeModifiers();           // 清除所有 modifier（防止被撑回去）
                }
                // 补真伤：用 CoreMod 反射钩子直接写 health 字段，绕过一切保护
                float remaining = target.getHealth();
                if (remaining > 0) {
                    com.Harbinger.Spore.coremod.CoreModHooks.applyTrueDamage(target, remaining);
                }
                Spore.LOGGER.info("[Boss AOE] " + boss.getType() + " 对 " + target.getType() + " 执行setMaxHealth(0)+真伤斩杀");
            }
            
            // 计算基于最大血量的伤害（类似炼狱结界）
            // 使用炼狱结界的精确公式：maxHealth / 17 / 20 / 20 + 0.01
            float percentAmount = target.getMaxHealth() / 17.0f / 20.0f / 20.0f + 0.01f;
            float percentDamage = target.getMaxHealth() * (data.damagePercent / 100.0f);
            
            // 选择较大的伤害值
            float finalDamage = Math.max(percentAmount, percentDamage);
            
            // 保存原始属性值（类似炼狱结界）
            double originalKnockback = 0.0;
            double originalMovement = 0.1;
            if (target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) != null) {
                originalKnockback = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).getBaseValue();
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
            }
            if (target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) != null) {
                originalMovement = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getBaseValue();
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
            }
            
            // 执行多连击（类似炼狱结界，增加攻击次数）
            int attackCount = 5 + random.nextInt(10); // 5-14连击（增加伤害频率）
            for (int i = 0; i < attackCount; i++) {
                // 1. 随机选择伤害源进行普通伤害
                DamageSource source = getRandomDamageSource(boss, target);
                
                // 尝试设置绕过护甲（类似炼狱结界）
                try {
                    // 使用反射设置bypassArmor（如果可用）
                    java.lang.reflect.Field bypassArmorField = source.getClass().getDeclaredField("bypassArmor");
                    bypassArmorField.setAccessible(true);
                    bypassArmorField.setBoolean(source, true);
                } catch (Exception e) {
                    // 如果无法设置bypassArmor，继续使用普通伤害
                }
                
                // 使用EntityActuallyHurt绕过无敌帧（对标炼狱结界）
                com.Harbinger.Spore.Sentities.util.EntityActuallyHurtUtil.actuallyHurt(target, source, finalDamage);
                
                data.attackCount++;
                // 使用普通攻击源再次调用hurt
                target.hurt(boss.damageSources().mobAttack(boss), finalDamage);
                
                // 重置目标无敌帧（借鉴炼狱结界）
                target.invulnerableTime = 0;
                
                data.attackCount++;
            }
            
            // 恢复属性值（类似炼狱结界）
            if (target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) != null) {
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(originalKnockback);
            }
            if (target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) != null) {
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(originalMovement);
            }
            
            // 冻结位置（类似炼狱结界）
            if (target.tickCount > 0) {
                target.setPos(target.xOld, target.yOld, target.zOld);
                target.setDeltaMovement(0.0, 0.0, 0.0);
            }

            // === CoreMod AOE 真伤 ===
            // 反射直接写 health 字段，绕过 bytecode 限伤 + 护甲 + 无敌帧
            // 极寒附魔持有者已在目标过滤中排除（shouldIgnoreAOE）
            float trueDmg = target.getMaxHealth() * 0.02f + 1.0f; // 2% max HP + 1 固定
            com.Harbinger.Spore.coremod.CoreModHooks.applyTrueDamage(target, trueDmg);
        }

        data.tickCount++;
        
        if (data.tickCount % 20 == 0) { // 每20帧输出一次日志
            Spore.LOGGER.info("[Boss AOE] " + boss.getType() + " AOE Tick " + data.tickCount + " 击中 " + targets.size() + " 个目标 总攻击次数: " + data.attackCount);
        }
    }
    
    /**
     * 获取随机伤害源（简化版炼狱结界的30+种伤害源）
     */
    private static DamageSource getRandomDamageSource(LivingEntity boss, LivingEntity target) {
        int sourceType = random.nextInt(15); // 15种伤害类型
        
        switch (sourceType) {
            case 0: return boss.damageSources().mobAttack(boss);
            case 1: return boss.damageSources().thorns(boss);
            case 2: return boss.damageSources().indirectMagic(boss, boss);
            case 3: return boss.damageSources().mobProjectile(boss, boss);
            case 4: return boss.damageSources().indirectMagic(boss, boss);
            case 5: return boss.damageSources().mobAttack(boss);
            case 6: return boss.damageSources().indirectMagic(boss, boss);
            case 7: return boss.damageSources().mobAttack(boss);
            case 8: return boss.damageSources().indirectMagic(boss, boss);
            case 9: return boss.damageSources().mobAttack(boss);
            case 10: return boss.damageSources().thorns(boss);
            case 11: return boss.damageSources().indirectMagic(boss, boss);
            case 12: return boss.damageSources().mobAttack(boss);
            case 13: return boss.damageSources().indirectMagic(boss, boss);
            case 14: return boss.damageSources().mobAttack(boss);
            default: return boss.damageSources().mobAttack(boss);
        }
    }
    
    /**
     * 执行AOE攻击（单次触发）
     */
    public static void performAOEAttack(LivingEntity boss, float range, float damagePercent, int attackCount) {
        if (boss.level().isClientSide || !boss.isAlive()) {
            return;
        }
        
        Level level = boss.level();
        AABB aabb = boss.getBoundingBox().inflate(range);
        
        // 获取范围内的目标
        java.util.List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, aabb,
            entity -> entity != boss
                    && entity.isAlive()
                    && !(entity instanceof Player player && player.isCreative())
                    && !isSporeEntity(entity)
                    && !com.Harbinger.Spore.Senchantments.ExtremeFrostEnchantment.shouldIgnoreAOE(entity));

        // 对每个目标造成多连击伤害
        for (LivingEntity target : targets) {
            float maxHealth = target.getMaxHealth();
            float percentDamage = maxHealth * (damagePercent / 100.0f);
            
            for (int i = 0; i < attackCount; i++) {
                DamageSource source = getRandomDamageSource(boss, target);
                target.hurt(source, percentDamage / attackCount);
                
                // 重置目标无敌帧
                target.invulnerableTime = 0;
            }
        }
        
        Spore.LOGGER.info("[Boss AOE] " + boss.getType() + " AOE攻击: 范围" + range + " 伤害" + damagePercent + "% 连击" + attackCount + " 击中 " + targets.size() + " 个目标");
    }
    
    /**
     * 检查Boss是否启用了AOE
     */
    public static boolean hasAOE(LivingEntity boss) {
        AOEData data = aoeData.get(boss);
        return data != null && data.enabled;
    }
    
    /**
     * 获取Boss AOE数据
     */
    public static AOEData getAOEData(LivingEntity boss) {
        return aoeData.get(boss);
    }
}