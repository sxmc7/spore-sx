package com.Harbinger.Spore.Sentities.BaseEntities;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.ExtremelySusThings.Utilities;
import com.Harbinger.Spore.Senchantments.ExtremeFrostEnchantment;
import com.Harbinger.Spore.Sentities.AI.HurtTargetGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class UtilityEntity extends PathfinderMob {
    protected UtilityEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        if (!com.Harbinger.Spore.Sentities.anticheat.DamageLimiter.isRegistered(this)) {
            float damageCap = Math.max(1.5f, this.getMaxHealth() * 0.01f);
            com.Harbinger.Spore.Sentities.anticheat.DamageLimiter.registerEntity(this);
            com.Harbinger.Spore.Sentities.anticheat.DamageLimiter.setDamageCap(this, damageCap);
        }
    }

    // ======== 独立血量存储（WeakHashMap 自动 GC）========

    @Override
    public float getHealth() {
        float stored = com.Harbinger.Spore.Sentities.anticheat.SporeHealthStorage.getHealth(this);
        float sup = super.getHealth();
        if (stored > 0f && Math.abs(stored - sup) > 0.5f) {
            com.Harbinger.Spore.Spore.LOGGER.info("[Def] {} getHealth 存储={} 原版={}",
                    this.getClass().getSimpleName(), String.format("%.1f", stored), String.format("%.1f", sup));
        }
        return stored > 0f ? stored : sup;
    }

    @Override
    public void setHealth(float health) {
        float current = com.Harbinger.Spore.Sentities.anticheat.SporeHealthStorage.getHealth(this);
        if (current <= 0f) current = super.getHealth(); // fallback: 存储未初始化时取原版值
        float prevV = super.getHealth();
        float originalParam = health;

        // 外部 改血 bypass 保护 (CoreMod ASM fallback)
        // 无条件应用窗口限伤 + 1% 单次限伤 + 自适应无敌帧
        if (current > 0.0f && health < current
                && !IN_HURT_CHAIN.get()
                && !com.Harbinger.Spore.util.HealthFieldUtil.isAuthorized()) {
            float reduction = current - health;

            if (com.Harbinger.Spore.Sentities.anticheat.DamageLimiter.isBypassInvulnerable(this)) {
                com.Harbinger.Spore.Spore.LOGGER.info("[Def] Bypass无敌帧阻挡: reduction={}", String.format("%.1f", reduction));
                health = current;
            } else {
                float pctCap = Math.max(current * 0.01f, 0.5f);
                float frameAllowed = com.Harbinger.Spore.Sentities.anticheat.DamageLimiter.applyFrameCap(this, reduction);
                float allowedReduction = Math.min(reduction, Math.min(frameAllowed, pctCap));
                if (allowedReduction < reduction) {
                    com.Harbinger.Spore.Spore.LOGGER.info("[Def] 外部改血拦截: reduction={} -> allowed={} (pctCap={} frame={})",
                            String.format("%.1f", reduction), String.format("%.1f", allowedReduction),
                            String.format("%.1f", pctCap), String.format("%.1f", frameAllowed));
                }
                com.Harbinger.Spore.Sentities.anticheat.DamageLimiter.recordBypassHit(this);
                health = Math.max(0.0f, current - allowedReduction);
            }
        }

        super.setHealth(health);

        float actual = super.getHealth();
        com.Harbinger.Spore.Sentities.anticheat.SporeHealthStorage.setHealth(this, actual);

        if (Math.abs(originalParam - current) > 1.0f || Math.abs(prevV - actual) > 0.5f) {
            com.Harbinger.Spore.Spore.LOGGER.info("[Def] {} setHealth({}) 存储 {}→{} 原版 {}→{}",
                    this.getClass().getSimpleName(), String.format("%.1f", originalParam),
                    String.format("%.1f", current), String.format("%.1f", actual),
                    String.format("%.1f", prevV), String.format("%.1f", actual));
        }
    }
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }
    public List<? extends String> getDropList(){
        return null;
    }

    private static final ThreadLocal<Boolean> IN_HURT_CHAIN = ThreadLocal.withInitial(() -> false);

    // === Spore武器/极寒附魔 真伤后门 ===
    @Override
    public boolean hurt(DamageSource source, float amount) {
        com.Harbinger.Spore.Sentities.anticheat.DamageLimiter.registerEntity(this);
        float limitedAmount = com.Harbinger.Spore.Sentities.anticheat.DamageLimiter.limitDamage(this, source, amount);

        if (source.getEntity() instanceof LivingEntity attacker) {
            // 极寒附魔真伤（不变）
            if (ExtremeFrostEnchantment.hasExtremeFrost(attacker)) {
                int frostLevel = ExtremeFrostEnchantment.getExtremeFrostLevel(attacker);
                float totalTrueDamage = 3.0f + this.getMaxHealth() * 0.03f * frostLevel;
                com.Harbinger.Spore.util.HealthFieldUtil.addHealth(this, -totalTrueDamage);
            }
            // Spore武器百分比真伤后门（和极寒一样的绕过机制，百分比取配置值）
            if (!attacker.getMainHandItem().isEmpty() && attacker.getMainHandItem().getItem() instanceof com.Harbinger.Spore.Sitems.BaseWeapons.SporeWeaponData) {
                double pct = com.Harbinger.Spore.Core.SConfig.SERVER.default_percentage_damage.get();
                float trueDamage = this.getMaxHealth() * (float)(pct / 100.0);
                com.Harbinger.Spore.Spore.LOGGER.info("[UtilityEntity] Spore武器后门真伤: attacker={} 武器={} 百分比={}% 目标最大生命={} 真伤={}",
                        attacker.getClass().getSimpleName(),
                        attacker.getMainHandItem().getItem().getClass().getSimpleName(),
                        pct, this.getMaxHealth(), trueDamage);
                if (trueDamage >= 1.0f) {
                    com.Harbinger.Spore.util.HealthFieldUtil.addHealth(this, -trueDamage);
                    // 禁疗标记（LivingHealEvent/CoreMod双保险）
                    com.Harbinger.Spore.Spore.LOGGER.info("[UtilityEntity] Spore武器后门真伤+禁疗: target={} 真伤={}",
                            this.getClass().getSimpleName(), trueDamage);
                    this.getPersistentData().putBoolean("spore_frost_antiheal", true);
                    this.getPersistentData().putLong("spore_frost_antiheal_time",
                            this.level().getGameTime() + 60);
                    // 追伤标记 — 每tick持续扣血，总量150%·5tick，破亚波伦限伤
                    this.getPersistentData().putFloat("spore_hp_keeper", trueDamage * 0.3f);
                    this.getPersistentData().putInt("spore_hp_keeper_ticks", 5);
                }
            }
        }
        IN_HURT_CHAIN.set(true);
        try {
            return super.hurt(source, limitedAmount);
        } finally {
            IN_HURT_CHAIN.set(false);
        }
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        float f = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float f1 = (float) this.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        if (entity instanceof LivingEntity) {
            f += EnchantmentHelper.getDamageBonus(this.getMainHandItem(), ((LivingEntity) entity).getMobType());
            f1 += (float) EnchantmentHelper.getKnockbackBonus(this);
        }

        int i = EnchantmentHelper.getFireAspect(this);
        if (i > 0) {
            entity.setSecondsOnFire(i * 4);
        }

        boolean flag = false;
        boolean forceBypass = false;

        // 通道1: UnsafeHealthHelper 直写改血 bypass（跳过创造模式玩家）
        if (com.Harbinger.Spore.Core.SConfig.SERVER.enable_force_set_health.get()
                && entity instanceof LivingEntity livingTarget
                && !hasSporeArmor(livingTarget)
                && !(livingTarget instanceof net.minecraft.world.entity.player.Player p && p.isCreative())) {
            double reduction = com.Harbinger.Spore.Core.SConfig.SERVER.force_health_reduction.get();
            float trueHealth = com.Harbinger.Spore.util.UnsafeHealthHelper.getHealth(livingTarget);
            float newHealth = Math.max(0.0f, trueHealth - (float)reduction);
            com.Harbinger.Spore.util.UnsafeHealthHelper.setHealth(livingTarget, newHealth);
            livingTarget.getPersistentData().putBoolean("spore_frost_antiheal", true);
            livingTarget.getPersistentData().putLong("spore_frost_antiheal_time", livingTarget.level().getGameTime() + 60);
            forceBypass = true;
        }

        // 通道2: 普通伤害
        if (entity instanceof LivingEntity liv) { com.Harbinger.Spore.coremod.CoreModHooks.clearRealInvuln(liv); }
        if (entity instanceof LivingEntity) {
            flag = entity.hurt(getCustomDamage(this), f) || forceBypass;
        } else {
            flag = entity.hurt(getCustomDamage(this), f) || forceBypass;
        }

        if (flag) {
            if (f1 > 0.0F && entity instanceof LivingEntity livingEntity) {
                livingEntity.knockback((double) (f1 * 0.5F), (double) Mth.sin(this.getYRot() * ((float) Math.PI / 180F)), (double) (-Mth.cos(this.getYRot() * ((float) Math.PI / 180F))));
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
            }

            this.doEnchantDamageEffects(this, entity);
            this.setLastHurtMob(entity);
        }
        if (entity instanceof Player player) {
            this.maybeDisableShield(player, this.getMainHandItem(), player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY);
        }
        return flag;
    }
    private static boolean hasSporeArmor(LivingEntity entity) {
        for (ItemStack armorSlot : entity.getArmorSlots()) {
            if (armorSlot.getItem() instanceof com.Harbinger.Spore.Sitems.BaseWeapons.SporeArmorData) {
                return true;
            }
        }
        return false;
    }
    public void maybeDisableShield(Player p_21425_, ItemStack p_21426_, ItemStack p_21427_) {
        if (!p_21426_.isEmpty() && !p_21427_.isEmpty() && p_21426_.getItem() instanceof AxeItem && p_21427_.is(Items.SHIELD)) {
            float f = 0.25F + (float)EnchantmentHelper.getBlockEfficiency(this) * 0.05F;
            if (this.random.nextFloat() < f) {
                p_21425_.getCooldowns().addCooldown(Items.SHIELD, 100);
                this.level().broadcastEntityEvent(p_21425_, (byte)30);
            }
        }

    }
    public DamageSource getCustomDamage(LivingEntity entity){
        return this.damageSources().mobAttack(this);
    }


    public Predicate<LivingEntity> TARGET_SELECTOR = (entity) -> {
        return Utilities.TARGET_SELECTOR.Test(entity);
    };

    protected void addTargettingGoals(){
        this.goalSelector.addGoal(2, new HurtTargetGoal(this ,livingEntity -> {return TARGET_SELECTOR.test(livingEntity);}, Infected.class).setAlertOthers(Infected.class));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>
                (this, LivingEntity.class,  true,livingEntity -> {return livingEntity instanceof Player || SConfig.SERVER.whitelist.get().contains(livingEntity.getEncodeId());}){
            @Override
            protected AABB getTargetSearchArea(double value) {
                return this.mob.getBoundingBox().inflate(value, value, value);
            }
        });
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>
                (this, LivingEntity.class,  true, livingEntity -> {return SConfig.SERVER.at_mob.get() && TARGET_SELECTOR.test(livingEntity);}){
            @Override
            protected AABB getTargetSearchArea(double value) {
                return this.mob.getBoundingBox().inflate(value, value, value);
            }
        });
    }
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int val, boolean bool) {
        super.dropCustomDeathLoot(source, val, bool);
        if (getDropList() == null){return;}
        if (!getDropList().isEmpty()){
            for (String str : getDropList()){
                String[] string = str.split("\\|" );
                ItemStack itemStack = new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(new ResourceLocation(string[0]))));
                int m = 1;
                if (Integer.parseUnsignedInt(string[2]) == Integer.parseUnsignedInt(string[3])){
                    int o = Integer.parseUnsignedInt(string[3]);
                    m = val > 0 ? random.nextInt(o,o+val) : o;

                } else {if (Integer.parseUnsignedInt(string[2]) >= 1 && Integer.parseUnsignedInt(string[2]) >= 1){
                    int v1 = Integer.parseUnsignedInt(string[2]);
                    int v2 = Integer.parseUnsignedInt(string[3]);
                    float e = m * (0.15f * val);
                    int i = e > val ? (int) e : val;
                    m = random.nextInt(v1, v2+i);
                }}
                int value = Integer.parseUnsignedInt(string[1])+(val*10);
                if (Math.random() < (value / 100F)) {
                    itemStack.setCount(m);
                    ItemEntity item = new ItemEntity(level(), this.getX() , this.getY(),this.getZ(),itemStack);
                    item.setPickUpDelay(10);
                    level().addFreshEntity(item);}}
        }
    }

    protected boolean Cold(){
        BlockPos pos = new BlockPos(this.getBlockX(),this.getBlockY(),this.getBlockZ());
        Biome biome = level().getBiome(pos).value();
        return SConfig.SERVER.weaktocold.get() && biome.getBaseTemperature() <= 0.2;
    }

    public String getMutation(){
        return null;
    }
}
