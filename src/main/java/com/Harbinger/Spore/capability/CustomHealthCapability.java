package com.Harbinger.Spore.capability;

/**
 * ICustomHealth 实现 — 血量存储与 5% 限伤逻辑。
 *
 * 三字段：customHealth / customMaxHealth / active
 * - 激活时：所有血量操作落地至此，与 MC 原版 health 字段完全解耦
 * - 未激活时：passthrough，Mixins 不做任何拦截
 */
public class CustomHealthCapability implements ICustomHealth {
    private float customHealth = 20.0f;
    private float customMaxHealth = 20.0f;
    private boolean active = false;

    @Override
    public float getCustomHealth() {
        return customHealth;
    }

    @Override
    public void setCustomHealth(float health) {
        this.customHealth = Math.max(0.0f, Math.min(health, customMaxHealth));
    }

    @Override
    public float getCustomMaxHealth() {
        return customMaxHealth;
    }

    @Override
    public void setCustomMaxHealth(float maxHealth) {
        this.customMaxHealth = Math.max(1.0f, maxHealth);
        if (this.customHealth > this.customMaxHealth) {
            this.customHealth = this.customMaxHealth;
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }
}
