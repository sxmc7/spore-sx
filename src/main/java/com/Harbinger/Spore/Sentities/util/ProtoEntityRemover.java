package com.Harbinger.Spore.Sentities.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Proto 实体清除工具 —— 直接 discard，不绕血量系统。
 */
public class ProtoEntityRemover {

    private static boolean isSporeEntity(Entity entity) {
        return entity instanceof com.Harbinger.Spore.Sentities.BaseEntities.Infected
            || entity instanceof com.Harbinger.Spore.Sentities.BaseEntities.EvolvedInfected
            || entity instanceof com.Harbinger.Spore.Sentities.BaseEntities.Organoid
            || entity instanceof com.Harbinger.Spore.Sentities.BaseEntities.Calamity
            || entity instanceof com.Harbinger.Spore.Sentities.BaseEntities.Hyper
            || entity instanceof com.Harbinger.Spore.Sentities.BaseEntities.Experiment
            || entity instanceof com.Harbinger.Spore.Sentities.BaseEntities.UtilityEntity;
    }

    public static void eraseEntity(LivingEntity proto, float range) {
        if (proto.level().isClientSide || !proto.isAlive()) return;

        Level level = proto.level();
        AABB aabb = proto.getBoundingBox().inflate(range);

        List<Entity> entities = level.getEntitiesOfClass(Entity.class, aabb,
            entity -> entity != proto
                    && !(entity instanceof net.minecraft.world.entity.player.Player)
                    && !isSporeEntity(entity));

        int count = 0;
        for (Entity entity : entities) {
            if (entity.isAlive()) {
                entity.discard();
                count++;
            }
        }
        if (count > 0) {
            com.Harbinger.Spore.Spore.LOGGER.info("[Proto] 清除 {} 个实体", count);
        }
    }

    public static void instantKill(LivingEntity proto, Entity target) {
        if (target == null
                || target instanceof net.minecraft.world.entity.player.Player
                || isSporeEntity(target)) {
            return;
        }
        target.discard();
    }
}
