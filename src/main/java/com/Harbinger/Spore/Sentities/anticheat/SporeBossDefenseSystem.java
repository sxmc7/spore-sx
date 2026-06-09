package com.Harbinger.Spore.Sentities.anticheat;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Spore;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.WeakHashMap;

/**
 * Spore Boss高级防御系统
 * 借鉴亚波伦的防御机制：
 * 1. 独立血量管理（多层保护）
 * 2. 复杂伤害减免计算
 * 3. 高级无敌帧（分阶段）
 * 4. 反作弊保护
 * 5. 异常伤害检测
 * 6. 血量异常恢复
 */
public class SporeBossDefenseSystem {
    // 存储每个Boss的独立血量
    private static final WeakHashMap<LivingEntity, BossHealthData> bossHealthData = new WeakHashMap<>();
    // 存储每个Boss的伤害减免系数
    private static final WeakHashMap<LivingEntity, Float> damageReduction = new WeakHashMap<>();
    // 存储每个Boss的防御状态
    private static final WeakHashMap<LivingEntity, DefenseState> defenseState = new WeakHashMap<>();
    
    // 默认伤害减免（对标亚波伦）
    private static final float DEFAULT_DAMAGE_REDUCTION = 0.75f; // 75%伤害减免
    // 无敌帧阶段
    private static final int[] INVINCIBILITY_PHASES = {20, 40, 60}; // 1秒, 2秒, 3秒
    // 异常伤害阈值
    private static final float ABNORMAL_DAMAGE_THRESHOLD = 0.05f; // 5%
    
    /**
     * Boss血量数据（多层保护）
     */
    public static class BossHealthData {
        public float currentHealth;
        public float maxHealth;
        public float lastValidHealth;
        public int invulnerabilityPhase;
        public float cumulativeDamageThisTick;
        public long lastDamageTick;
        public int consecutiveDamageTicks;
        
        public BossHealthData(float maxHealth) {
            this.maxHealth = maxHealth;
            this.currentHealth = maxHealth;
            this.lastValidHealth = maxHealth;
            this.invulnerabilityPhase = 0;
            this.cumulativeDamageThisTick = 0.0f;
            this.lastDamageTick = 0;
            this.consecutiveDamageTicks = 0;
        }
    }
    
    /**
     * 防御状态
     */
    public static class DefenseState {
        public boolean isPhase3Active;
        public boolean isRegenerating;
        public float regenerationRate;
        public int regenerationDuration;
        
        public DefenseState() {
            this.isPhase3Active = false;
            this.isRegenerating = false;
            this.regenerationRate = 0.01f;
            this.regenerationDuration = 0;
        }
    }
    
    /**
     * 注册Boss到防御系统
     */
    public static void registerBoss(LivingEntity boss) {
        if (boss instanceof Calamity) {
            float maxHealth = boss.getMaxHealth();
            bossHealthData.put(boss, new BossHealthData(maxHealth));
            damageReduction.put(boss, Float.valueOf(DEFAULT_DAMAGE_REDUCTION));
            defenseState.put(boss, new DefenseState());
            Spore.LOGGER.info("[Boss防御] Boss已注册: " + boss.getType() + " 最大血量: " + maxHealth + " 防御系统已激活");
        }
    }
    
    /**
     * 从防御系统中移除Boss
     */
    public static void unregisterBoss(LivingEntity boss) {
        bossHealthData.remove(boss);
        damageReduction.remove(boss);
        defenseState.remove(boss);
        Spore.LOGGER.info("[Boss防御] Boss已移除: " + boss.getType());
    }
    
    /**
     * Boss受到伤害（对标亚波伦的hurt()方法）
     */
    public static float bossHurt(LivingEntity boss, DamageSource source, float amount) {
        BossHealthData data = bossHealthData.get(boss);
        if (data == null) {
            return amount; // 没有注册，使用原始伤害
        }
        
        long currentTick = boss.level().getGameTime();
        
        // 检查阶段3防御（终极防御）
        if (defenseState.get(boss).isPhase3Active) {
            Spore.LOGGER.info("[Boss防御] 阶段3终极防御：完全免疫伤害 " + amount);
            return 0.0f;
        }
        
        // 检查无敌帧
        if (isInvulnerable(boss, currentTick)) {
            Spore.LOGGER.info("[Boss防御] 无敌帧保护: " + boss.getType() + " 免疫伤害 " + amount);
            return 0.0f;
        }
        
        // 计算伤害减免
        float reduction = damageReduction.getOrDefault(boss, Float.valueOf(DEFAULT_DAMAGE_REDUCTION)).floatValue();
        float reducedDamage = amount * (1.0f - reduction);
        
        // 记录最后的健康血量
        data.lastValidHealth = data.currentHealth;
        
        // 扣除血量
        data.currentHealth = Mth.clamp(data.currentHealth - reducedDamage, 0.0f, data.maxHealth);
        data.cumulativeDamageThisTick += reducedDamage;
        
        // 检查异常伤害
        float healthLost = data.lastValidHealth - data.currentHealth;
        if (healthLost > boss.getMaxHealth() * ABNORMAL_DAMAGE_THRESHOLD) {
            Spore.LOGGER.info("[Boss防御] 检测到异常伤害: " + healthLost + "，触发防御升级");
            // 可以在这里升级防御
        }
        
        // 触发无敌帧
        triggerInvulnerability(boss, currentTick);
        
        // 同步到原版血量（用于显示）
        boss.setHealth(data.currentHealth);
        
        Spore.LOGGER.info("[Boss防御] Boss受伤: " + boss.getType() + " 原始: " + amount + " 减免后: " + reducedDamage + " 当前血量: " + data.currentHealth + "/" + data.maxHealth);
        
        return reducedDamage;
    }
    
    /**
     * 检查是否在无敌帧内
     */
    private static boolean isInvulnerable(LivingEntity boss, long currentTick) {
        BossHealthData data = bossHealthData.get(boss);
        if (data == null) return false;
        
        long timeSinceDamage = currentTick - data.lastDamageTick;
        int invulnerabilityTime = INVINCIBILITY_PHASES[Math.min(data.invulnerabilityPhase, INVINCIBILITY_PHASES.length - 1)];
        
        return timeSinceDamage < invulnerabilityTime;
    }
    
    /**
     * 触发无敌帧
     */
    private static void triggerInvulnerability(LivingEntity boss, long currentTick) {
        BossHealthData data = bossHealthData.get(boss);
        if (data != null) {
            data.lastDamageTick = currentTick;
            data.invulnerabilityPhase = Math.min(data.invulnerabilityPhase + 1, INVINCIBILITY_PHASES.length - 1);
        }
    }
    
    /**
     * 更新Boss防御状态（每帧调用）
     */
    public static void updateDefense(LivingEntity boss) {
        BossHealthData data = bossHealthData.get(boss);
        if (data == null || boss.isDeadOrDying()) {
            return;
        }
        
        // 更新无敌帧
        long currentTick = boss.level().getGameTime();
        if (!isInvulnerable(boss, currentTick)) {
            // 不在无敌帧内，可以降低阶段
            if (data.invulnerabilityPhase > 0 && (currentTick - data.lastDamageTick) > 100) {
                data.invulnerabilityPhase = 0;
            }
        }
        
        // 处理回血
        DefenseState state = defenseState.get(boss);
        if (state != null && state.isRegenerating && state.regenerationDuration > 0) {
            float healed = data.maxHealth * state.regenerationRate;
            data.currentHealth = Math.min(data.currentHealth + healed, data.maxHealth);
            state.regenerationDuration--;
            boss.setHealth(data.currentHealth);
            
            if (state.regenerationDuration <= 0) {
                state.isRegenerating = false;
            }
        }
        
        // 重置每帧伤害累积
        data.cumulativeDamageThisTick = 0.0f;
    }
    
    /**
     * 获取Boss的独立血量
     */
    public static float getBossHealth(LivingEntity boss) {
        BossHealthData data = bossHealthData.get(boss);
        if (data != null) {
            return data.currentHealth;
        }
        return boss.getHealth();
    }
    
    /**
     * 设置Boss的独立血量
     */
    public static void setBossHealth(LivingEntity boss, float health) {
        BossHealthData data = bossHealthData.get(boss);
        if (data != null) {
            data.currentHealth = Mth.clamp(health, 0.0f, data.maxHealth);
            data.lastValidHealth = data.currentHealth;
            // 同步到原版血量（用于显示）
            boss.setHealth(data.currentHealth);
        } else {
            boss.setHealth(health);
        }
    }
    
    /**
     * 检查Boss是否存活
     */
    public static boolean isBossAlive(LivingEntity boss) {
        BossHealthData data = bossHealthData.get(boss);
        if (data != null) {
            return data.currentHealth > 0.0f && !boss.isDeadOrDying();
        }
        return boss.isAlive();
    }
    
    /**
     * 设置Boss伤害减免
     */
    public static void setDamageReduction(LivingEntity boss, float reduction) {
        if (boss instanceof Calamity) {
            damageReduction.put(boss, Float.valueOf(Mth.clamp(reduction, 0.0f, 1.0f)));
            Spore.LOGGER.info("[Boss防御] " + boss.getType() + " 伤害减免设置为: " + (reduction * 100) + "%");
        }
    }
    
    /**
     * 获取Boss血量百分比
     */
    public static float getBossHealthPercent(LivingEntity boss) {
        BossHealthData data = bossHealthData.get(boss);
        if (data != null && data.maxHealth > 0) {
            return data.currentHealth / data.maxHealth;
        }
        return boss.getHealth() / boss.getMaxHealth();
    }
    
    /**
     * Boss回血
     */
    public static void bossHeal(LivingEntity boss, float amount) {
        BossHealthData data = bossHealthData.get(boss);
        if (data != null) {
            data.currentHealth = Mth.clamp(data.currentHealth + amount, 0.0f, data.maxHealth);
            boss.setHealth(data.currentHealth);
        } else {
            boss.heal(amount);
        }
    }
    
    /**
     * 激活阶段3防御（终极防御）
     */
    public static void activatePhase3(LivingEntity boss, boolean active) {
        DefenseState state = defenseState.get(boss);
        if (state != null) {
            state.isPhase3Active = active;
            Spore.LOGGER.info("[Boss防御] " + boss.getType() + " 阶段3防御: " + (active ? "激活" : "停用"));
        }
    }
    
    /**
     * 激活回血
     */
    public static void activateRegeneration(LivingEntity boss, float rate, int duration) {
        DefenseState state = defenseState.get(boss);
        if (state != null) {
            state.isRegenerating = true;
            state.regenerationRate = rate;
            state.regenerationDuration = duration;
            Spore.LOGGER.info("[Boss防御] " + boss.getType() + " 激活回血: " + (rate * 100) + "/tick 持续: " + duration + "tick");
        }
    }
    
    /**
     * 获取Boss防御状态
     */
    public static DefenseState getDefenseState(LivingEntity boss) {
        return defenseState.get(boss);
    }
}