package com.Harbinger.Spore.mixin;

import com.Harbinger.Spore.capability.CustomHealthRegistry;
import com.Harbinger.Spore.capability.ICustomHealth;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 自定义血量 Mixin — 全方法劫持。
 *
 * 核心方案：
 *   当目标实体的 ICustomHealth Capability 激活时，所有血量相关方法
 * （getHealth / setHealth / hurt / getMaxHealth）全部重定向到
 * capability 存储，使 LivingEntity.health 字段成为诱饵。
 *
 * 保护措施：
 *   1. 5% 最大血量 / 次 的 setHealth 限伤
 *   2. 5% 最大血量 / hit 的 hurt 限伤（叠加原版 invulnerableTime）
 *   3. ThreadLocal 重入守卫防止 hurt(0) 无线递归
 *
 * 非 Spore 实体（capability 未激活）完全 passthrough，不受影响。
 */
@Mixin(value = LivingEntity.class, priority = 1100)
public abstract class CustomHealthMixin {

    @Shadow
    private int invulnerableTime;

    /** 重入守卫 — onHurt 内调用原版 hurt(0) 时防止递归 */
    private static final ThreadLocal<Boolean> HURT_LOCK =
            ThreadLocal.withInitial(() -> false);

    // ======== 辅助方法 ========

    private ICustomHealth $getCap() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (CustomHealthRegistry.CUSTOM_HEALTH == null) return null;
        return self.getCapability(CustomHealthRegistry.CUSTOM_HEALTH).orElse(null);
    }

    // ======== getHealth — 返回 Capability 血量 ========

    @Inject(method = "getHealth", at = @At("RETURN"), cancellable = true)
    private void onGetHealth(CallbackInfoReturnable<Float> cir) {
        ICustomHealth cap = $getCap();
        if (cap == null || !cap.isActive()) return;
        cir.setReturnValue(cap.getCustomHealth());
    }

    // ======== setHealth — 重定向至 Capability + 5% 限伤 ========

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void onSetHealth(float health, CallbackInfo ci) {
        ICustomHealth cap = $getCap();
        if (cap == null || !cap.isActive()) return;

        float maxHealth = cap.getCustomMaxHealth();
        float current = cap.getCustomHealth();
        float change = current - health; // 正数 = 扣血

        // 5% 最大血量限伤：单次 setHealth 最多扣 5% max HP
        if (change > 0) {
            float maxAllowed = Math.max(1.0f, maxHealth * 0.05f);
            if (change > maxAllowed) {
                health = current - maxAllowed;
            }
        }

        cap.setCustomHealth(Math.max(0.0f, health));
        ci.cancel();
    }

    // ======== hurt — 伤害落地到 Capability ========

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurt(DamageSource source, float amount,
                        CallbackInfoReturnable<Boolean> cir) {
        // 重入守卫：onHurt 内调 self.hurt(0) 时放行原方法体
        if (HURT_LOCK.get()) return;

        LivingEntity self = (LivingEntity) (Object) this;
        ICustomHealth cap = $getCap();
        if (cap == null || !cap.isActive()) return;

        // 无敌帧检查
        if (invulnerableTime > 0
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        // 5% 限伤：单次 hit 最多扣 5% max HP
        float maxDamage = Math.max(1.0f, cap.getCustomMaxHealth() * 0.05f);
        float actualDamage = Math.min(amount, maxDamage);

        // 应用伤害到自定义血量
        float current = cap.getCustomHealth();
        float newHealth = Math.max(0.0f, current - actualDamage);
        cap.setCustomHealth(newHealth);

        // 设置无敌帧
        invulnerableTime = 10;

        // 调原版 hurt(0) 触发游戏事件（击退、声音、附魔等）
        HURT_LOCK.set(true);
        boolean result = self.hurt(source, 0.0f);
        HURT_LOCK.set(false);

        // 血量归零则死亡
        if (newHealth <= 0.0f && self.isAlive()) {
            self.die(source);
        }

        cir.setReturnValue(result || actualDamage > 0.0f);
        cir.cancel();
    }

    // ======== getMaxHealth — 返回 Capability 最大血量 ========

    @Inject(method = "getMaxHealth", at = @At("RETURN"), cancellable = true)
    private void onGetMaxHealth(CallbackInfoReturnable<Float> cir) {
        ICustomHealth cap = $getCap();
        if (cap == null || !cap.isActive()) return;
        cir.setReturnValue(cap.getCustomMaxHealth());
    }
}
