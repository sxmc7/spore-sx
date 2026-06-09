package com.Harbinger.Spore.capability;

/**
 * 自定义血量存储接口 — 通过 Forge Capability 解耦 LivingEntity.health。
 * Spore 实体的血量存储在此而非 MC 原版 health 字段，使得原字段成为诱饵。
 */
public interface ICustomHealth {
    /** 获取自定义血量 */
    float getCustomHealth();

    /** 设置自定义血量（自动 clamp 到 [0, maxHealth]） */
    void setCustomHealth(float health);

    /** 获取自定义最大血量 */
    float getCustomMaxHealth();

    /** 设置自定义最大血量 */
    void setCustomMaxHealth(float maxHealth);

    /** 该 Capability 是否激活 */
    boolean isActive();

    /** 设置激活状态 */
    void setActive(boolean active);
}
