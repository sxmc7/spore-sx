package com.Harbinger.Spore.Senchantments;

import com.Harbinger.Spore.Core.Senchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * 极寒附魔 - 终极 Spore 杀手
 *
 * 功能：
 * 1. 无视 Spore 生物限伤保护（DamageLimiter 白名单）
 * 2. 真伤（直接 setHealth，无视护甲/限伤/无敌帧/附魔保护）
 * 3. 冻结目标 1 秒（强力缓慢 + 冰冻纹理，模仿冈格尼尔）
 * 4. 禁疗 10 秒（NBT 标记 + CoreMod ASM 拦截 heal()）
 * 5. 手持时无视 Spore 生物 AOE 攻击
 * 6. 持盾时免疫 Spore 生物伤害
 */
public class ExtremeFrostEnchantment extends Enchantment {
    
    public ExtremeFrostEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    
    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }

    @Override
    public boolean isDiscoverable() {
        return true;
    }

    @Override
    public int getMinCost(int level) {
        return 15 + (level - 1) * 10;
    }

    @Override
    public int getMaxCost(int level) {
        return this.getMinCost(level) + 15;
    }
    
    /**
     * 只能附魔武器、弓箭、盾牌
     */
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.SwordItem ||
               stack.getItem() instanceof net.minecraft.world.item.BowItem ||
               stack.getItem() instanceof net.minecraft.world.item.CrossbowItem ||
               stack.getItem() instanceof net.minecraft.world.item.TridentItem ||
               stack.getItem() instanceof ShieldItem;
    }
    
    /**
     * 攻击后触发 —— 冻结 + 禁疗
     *
     * 冻结：NBT 标记 + CoreMod 拦截 travel()/serverAiStep() 实现时停
     * 禁疗：NBT 标记 + CoreMod 拦截 heal() 实现
     * 真伤：由目标实体类（Infected/UtilityEntity）的 hurt() 后门处理
     */
    @Override
    public void doPostAttack(LivingEntity attacker, Entity target, int level) {
        if (target instanceof LivingEntity livingTarget) {
            CompoundTag tag = livingTarget.getPersistentData();

            // 1. 冻结标记（时停 1 秒 = 20 tick，CoreMod 在 travel/serverAiStep 拦截）
            tag.putLong("spore_freeze_until", livingTarget.level().getGameTime() + (20 * level));

            // 2. 禁疗标记（CoreMod 在 heal() 入口拦截）
            tag.putBoolean("spore_frost_antiheal", true);
            tag.putLong("spore_frost_antiheal_time", livingTarget.level().getGameTime() + 200); // 10秒
        }
    }
    
    /**
     * 获取伤害倍率（用于 LivingHurtEvent 中应用）
     * @param level 附魔等级
     * @return 伤害倍率
     */
    public static float getDamageMultiplier(int level) {
        return 3.0f + (level - 1) * 0.5f; // 1级=3倍，2级=3.5倍，3级=4倍
    }
    
    /**
     * 检查实体是否手持极寒附魔武器
     */
    public static boolean hasExtremeFrost(LivingEntity entity) {
        ItemStack mainHand = entity.getMainHandItem();
        int level = EnchantmentHelper.getItemEnchantmentLevel(
            Senchantments.EXTREME_FROST.get(), mainHand);
        return level > 0;
    }
    
    /**
     * 获取极寒附魔等级
     */
    public static int getExtremeFrostLevel(LivingEntity entity) {
        ItemStack mainHand = entity.getMainHandItem();
        return EnchantmentHelper.getItemEnchantmentLevel(
            Senchantments.EXTREME_FROST.get(), mainHand);
    }
    
    /**
     * 检查物品上是否有极寒附魔
     */
    public static boolean hasExtremeFrostOnItem(ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(
                Senchantments.EXTREME_FROST.get(), stack) > 0;
    }

    /**
     * 检查实体是否持有极寒附魔盾牌
     */
    public static boolean hasExtremeFrostShield(LivingEntity entity) {
        for (ItemStack stack : entity.getHandSlots()) {
            if (stack.getItem() instanceof ShieldItem && hasExtremeFrostOnItem(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查实体是否应该无视 AOE 攻击
     */
    public static boolean shouldIgnoreAOE(LivingEntity entity) {
        return hasExtremeFrost(entity);
    }
}
