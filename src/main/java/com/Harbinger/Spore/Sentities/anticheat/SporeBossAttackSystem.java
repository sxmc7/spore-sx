package com.Harbinger.Spore.Sentities.anticheat;

import com.Harbinger.Spore.Sentities.BaseEntities.*;
import com.Harbinger.Spore.Senchantments.ExtremeFrostEnchantment;
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
 * Spore Boss高级攻击系统
 * 借鉴炼狱结界的机制：
 * 1. 自带持续范围伤害（生成后一直存在）
 * 2. 多种攻击方式（包括setMaxHealth(0)、hurt方法调用）
 * 3. 重置目标无敌帧
 * 4. 可自定义伤害
 * 5. 区域攻击
 */
public class SporeBossAttackSystem {
    private static final Random random = new Random();
    
    // 攻击类型枚举
    public enum AttackType {
        NORMAL("普通攻击"),
        PIERCING("穿透攻击"),
        MAGIC("魔法攻击"),
        POISON("毒素攻击"),
        WITHER("凋零攻击"),
        FIRE("火焰攻击"),
        EXPLOSION("爆炸攻击"),
        KILL("即死攻击"),
        MAXHEALTH("清血攻击"),
        HURT_CALL("hurt调用攻击");
        
        private final String displayName;
        
        AttackType(String displayName) {
            this.displayName = displayName;
        }
    }
    
    // Boss持续范围伤害数据
    private static final WeakHashMap<LivingEntity, BarrierData> barrierData = new WeakHashMap<>();
    
    /**
     * 范围伤害数据
     */
    public static class BarrierData {
        public boolean enabled;
        public float damagePerTick;
        public float range;
        public int duration;
        public int currentTick;
        public int attackCount;
        
        public BarrierData(float damagePerTick, float range, int duration) {
            this.enabled = true;
            this.damagePerTick = damagePerTick;
            this.range = range;
            this.duration = duration;
            this.currentTick = 0;
            this.attackCount = 0;
        }
    }
    
    /**
     * 启用Boss持续范围伤害
     */
    public static void enableBarrier(LivingEntity boss, float damagePerTick, float range, int duration) {
        barrierData.put(boss, new BarrierData(damagePerTick, range, duration));
        Spore.LOGGER.info("[Boss攻击] " + boss.getType() + " 启用持续范围伤害: " + damagePerTick + "/tick 范围: " + range + " 持续时间: " + duration + "tick");
    }
    
    /**
     * 禁用Boss持续范围伤害
     */
    public static void disableBarrier(LivingEntity boss) {
        barrierData.remove(boss);
        Spore.LOGGER.info("[Boss攻击] " + boss.getType() + " 禁用持续范围伤害");
    }
    
    /**
     * 更新持续范围伤害（每帧调用）
     */
    public static void updateBarrier(LivingEntity boss) {
        BarrierData data = barrierData.get(boss);
        if (data == null || !data.enabled || boss.level().isClientSide || !boss.isAlive()) {
            return;
        }
        
        Level level = boss.level();
        AABB aabb = boss.getBoundingBox().inflate(data.range);
        
        // 获取范围内的目标
        java.util.List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, aabb, 
            entity -> entity != boss && entity.isAlive() && !(entity instanceof Player player && player.isCreative()));
        
        // 对每个目标造成伤害
        for (LivingEntity target : targets) {
            // 计算伤害百分比
            float percentAmount = target.getMaxHealth() / 17.0f / 20.0f / 20.0f + 0.01f;
            float finalDamage = Math.max(percentAmount, data.damagePerTick);
            
            // 造成伤害
            target.hurt(boss.damageSources().mobAttack(boss), finalDamage);
            
            // 重置目标无敌帧（借鉴炼狱结界）
            target.invulnerableTime = 0;
            
            data.attackCount++;
        }
        
        data.currentTick++;
        if (data.currentTick >= data.duration) {
            disableBarrier(boss);
        }
    }
    
    /**
     * Boss执行多种攻击（借鉴炼狱结界的多伤害源机制）
     */
    public static void performMultiAttack(LivingEntity boss, LivingEntity target, float baseDamageMultiplier) {
        if (boss.level().isClientSide || !boss.isAlive() || !target.isAlive()) {
            return;
        }
        
        Level level = boss.level();
        
        // 选择攻击组合
        int attackCount = 2 + random.nextInt(3); // 2-4种攻击
        
        for (int i = 0; i < attackCount; i++) {
            AttackType attackType = AttackType.values()[random.nextInt(AttackType.values().length)];
            performAttack(boss, target, attackType, baseDamageMultiplier);
            
            // 重置目标无敌帧（借鉴炼狱结界）
            target.invulnerableTime = 0;
        }
        
        Spore.LOGGER.info("[Boss攻击] " + boss.getType() + " 对 " + target.getType() + " 发起 " + attackCount + " 连击");
    }
    
    /**
     * 执行单一攻击（可自定义伤害）
     */
    private static void performAttack(LivingEntity boss, LivingEntity target, AttackType attackType, float damageMultiplier) {
        Level level = boss.level();
        float baseDamage = (float)boss.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        float finalDamage = baseDamage * damageMultiplier;
        
        switch (attackType) {
            case PIERCING:
                // 穿透攻击：120%伤害
                target.hurt(boss.damageSources().thorns(boss), finalDamage * 1.2f);
                break;
            case MAGIC:
                // 魔法攻击：80%伤害
                target.hurt(boss.damageSources().indirectMagic(boss, boss), finalDamage * 0.8f);
                break;
            case POISON:
                // 毒素攻击：50%伤害
                target.hurt(boss.damageSources().mobAttack(boss), finalDamage * 0.5f);
                break;
            case WITHER:
                // 凋零攻击：60%伤害
                target.hurt(boss.damageSources().mobAttack(boss), finalDamage * 0.6f);
                break;
            case FIRE:
                // 火焰攻击：40%伤害
                target.hurt(boss.damageSources().inFire(), finalDamage * 0.4f);
                break;
            case EXPLOSION:
                // 爆炸攻击：70%伤害
                target.hurt(boss.damageSources().explosion(boss, boss), finalDamage * 0.7f);
                break;
            case KILL:
                // 即死攻击：直接杀死（不通过hurt）
                target.setHealth(0.0f);
                target.discard();
                Spore.LOGGER.info("[Boss攻击] " + boss.getType() + " 对 " + target.getType() + " 使用即死攻击");
                break;
            case MAXHEALTH:
                // 清血攻击：设置血量为1（濒死状态）
                target.setHealth(1.0f);
                Spore.LOGGER.info("[Boss攻击] " + boss.getType() + " 对 " + target.getType() + " 使用清血攻击（setHealth=1）");
                break;
            case HURT_CALL:
                // hurt方法调用攻击：直接调用hurt方法
                target.hurt(boss.damageSources().mobAttack(boss), finalDamage);
                break;
            case NORMAL:
            default:
                // 普通攻击
                target.hurt(boss.damageSources().mobAttack(boss), finalDamage);
                break;
        }
        
        // 击退效果
        double knockback = 0.5 + random.nextDouble() * 0.5;
        Vec3 direction = target.position().subtract(boss.position()).normalize();
        target.knockback((float)knockback, direction.x, direction.z);
    }
    
    /**
     * 范围攻击（借鉴炼狱结界的区域伤害）
     */
    public static void performAOEAttack(LivingEntity boss, float range, int maxTargets, float damageMultiplier) {
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
                    && !ExtremeFrostEnchantment.shouldIgnoreAOE(entity));

        // 限制目标数量
        int targetsToHit = Math.min(targets.size(), maxTargets);
        
        for (int i = 0; i < targetsToHit; i++) {
            LivingEntity target = targets.get(i);
            performMultiAttack(boss, target, damageMultiplier);
        }
        
        Spore.LOGGER.info("[Boss攻击] " + boss.getType() + " AOE攻击击中 " + targetsToHit + " 个目标");
    }
    
    /**
     * 执行百分比伤害攻击（对标亚波伦的百分比伤害）
     */
    public static void performPercentDamageAttack(LivingEntity boss, LivingEntity target, float percent) {
        if (boss.level().isClientSide || !boss.isAlive() || !target.isAlive()) {
            return;
        }
        
        float maxHealth = target.getMaxHealth();
        float percentDamage = maxHealth * (percent / 100.0f);
        
        // 使用魔法伤害源（穿透性更强）
        target.hurt(boss.damageSources().indirectMagic(boss, boss), percentDamage);
        
        // 重置目标无敌帧
        target.invulnerableTime = 0;
        
        Spore.LOGGER.info("[Boss攻击] " + boss.getType() + " 对 " + target.getType() + " 造成 " + percent + "% 伤害 (" + percentDamage + "HP)");
    }
    
    /**
     * 执行真实伤害攻击（绕过所有防御）
     */
    public static void performTrueDamageAttack(LivingEntity boss, LivingEntity target, float damage) {
        if (boss.level().isClientSide || !boss.isAlive() || !target.isAlive()) {
            return;
        }
        
        // 直接修改血量（绕过所有防御）
        float currentHealth = target.getHealth();
        float newHealth = Math.max(0, currentHealth - damage);
        target.setHealth(newHealth);
        
        // 重置目标无敌帧
        target.invulnerableTime = 0;
        
        Spore.LOGGER.info("[Boss攻击] " + boss.getType() + " 对 " + target.getType() + " 造成 " + damage + " 真实伤害");
    }
    
    /**
     * 检查Boss是否启用了持续范围伤害
     */
    public static boolean hasBarrier(LivingEntity boss) {
        BarrierData data = barrierData.get(boss);
        return data != null && data.enabled;
    }
    
    /**
     * 获取Boss范围伤害数据
     */
    public static BarrierData getBarrierData(LivingEntity boss) {
        return barrierData.get(boss);
    }
}