package com.Harbinger.Spore.util;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.Senchantments.ExtremeFrostEnchantment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Spore模组的百分比伤害调度分发 — 通过 UnsafeHealthHelper 直写改血。
 */
public class SporeDamageDispatcher {

    private static final boolean DEBUG_MODE = true;

    /**
     * 对目标造成百分比伤害（直写改血，绕过护甲/限伤）
     * @param attacker 攻击者
     * @param target 目标
     * @param baseDamage 基础伤害
     */
    public static void dealPercentageDamage(Mob attacker, LivingEntity target, float baseDamage) {
        if (!SConfig.SERVER.enable_percentage_damage.get()) {
            logInfo("百分比伤害已禁用");
            return;
        }
        // 跳过创造模式玩家
        if (target instanceof net.minecraft.world.entity.player.Player p && p.isCreative()) {
            return;
        }

        double percentage = getPercentageDamage(attacker);
        float totalDamage = baseDamage;
        if (percentage > 0) {
            totalDamage += target.getMaxHealth() * (float)(percentage / 100.0);
        }

        logInfo("百分比伤害: " + attacker.getClass().getSimpleName() + " -> " + target.getClass().getSimpleName() +
                " 百分比: " + percentage + "% 基础: " + baseDamage + " 总计: " + totalDamage);

        float trueHealth = UnsafeHealthHelper.getHealth(target);
        float newHealth = Math.max(0.0f, trueHealth - totalDamage);
        UnsafeHealthHelper.setHealth(target, newHealth);
    }

    /**
     * 根据实体类型获取百分比伤害值
     */
    private static double getPercentageDamage(Mob entity) {
        String className = entity.getClass().getSimpleName();

        if (className.contains("Sieger") || className.contains("Leviathan") ||
            className.contains("Hinderburg") || className.contains("Stahlmorder") ||
            className.contains("Howitzer") || className.contains("Hohlfresser")) {
            return SConfig.SERVER.calamity_percentage_damage.get();
        }

        if (className.contains("Wendigo") || className.contains("Inquisitor") ||
            className.contains("Hevoker") || className.contains("Hvindicator") ||
            className.contains("Ogre") || className.contains("Grober") ||
            className.contains("Brot")) {
            return SConfig.SERVER.hyper_percentage_damage.get();
        }

        if (className.contains("Slasher") || className.contains("Brute") ||
            className.contains("Bloater") || className.contains("Howler") ||
            className.contains("Leaper") || className.contains("Stalker") ||
            className.contains("Knight") || className.contains("Griefer")) {
            return SConfig.SERVER.evolved_percentage_damage.get();
        }

        return SConfig.SERVER.default_percentage_damage.get();
    }

    /**
     * AOE百分比伤害
     */
    public static void dealAOEPercentageDamage(Mob attacker, float radius, float baseDamage) {
        if (!SConfig.SERVER.enable_percentage_damage.get()) {
            logInfo("AOE百分比伤害已禁用");
            return;
        }

        logInfo("AOE百分比伤害: " + attacker.getClass().getSimpleName() + " 范围: " + radius);

        for (net.minecraft.world.entity.Entity entity :
             attacker.level().getEntities(attacker, attacker.getBoundingBox().inflate(radius))) {
            if (entity instanceof LivingEntity livingTarget
                    && entity != attacker
                    && !ExtremeFrostEnchantment.shouldIgnoreAOE(livingTarget)) {
                dealPercentageDamage(attacker, livingTarget, baseDamage);
            }
        }
    }

    private static void logInfo(String message) {
        if (DEBUG_MODE) {
            com.Harbinger.Spore.Spore.LOGGER.info("[百分比伤害] " + message);
        }
    }

    public static String getSystemStatus() {
        StringBuilder status = new StringBuilder();
        status.append("=== 百分比伤害系统状态 ===\n");
        status.append("启用状态: ").append(SConfig.SERVER.enable_percentage_damage.get()).append("\n");
        status.append("默认百分比: ").append(SConfig.SERVER.default_percentage_damage.get()).append("%\n");
        status.append("进化百分比: ").append(SConfig.SERVER.evolved_percentage_damage.get()).append("%\n");
        status.append("超级进化百分比: ").append(SConfig.SERVER.hyper_percentage_damage.get()).append("%\n");
        status.append("灾难级百分比: ").append(SConfig.SERVER.calamity_percentage_damage.get()).append("%\n");
        status.append("穿透护甲: ").append(SConfig.SERVER.bypass_armor.get()).append("\n");
        status.append("穿透创造模式: ").append(SConfig.SERVER.bypass_creative.get()).append("\n");
        return status.toString();
    }
}
