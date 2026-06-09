package com.Harbinger.Spore.util;

import com.Harbinger.Spore.Sentities.anticheat.AccessChecker;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * 实体操作工具类 - 完整实现版本
 * 提供强制修改实体数据、位移、攻击等功能
 */
public class EntityUtil {
    
    /**
     * 强制设置实体数据字段
     * 通过反射修改私有字段，绕过访问控制
     */
    public static void forceSetEntityData(Object entityData, Object accessor, Object value) {
        try {
            AccessChecker.performPrivilegedAction(() -> {
                try {
                    // 方法1：尝试直接设置value字段
                    java.lang.reflect.Field dataField = entityData.getClass().getDeclaredField("value");
                    dataField.setAccessible(true);
                    dataField.set(entityData, value);
                } catch (Exception e) {
                    // 方法2：尝试反射设置itemsById中的数据项
                    try {
                        java.lang.reflect.Field itemsField = entityData.getClass().getDeclaredField("itemsById");
                        itemsField.setAccessible(true);
                        java.util.Map items = (java.util.Map) itemsField.get(entityData);
                        
                        if (items.containsKey(accessor)) {
                            Object dataItem = items.get(accessor);
                            java.lang.reflect.Field valueField = dataItem.getClass().getDeclaredField("value");
                            valueField.setAccessible(true);
                            valueField.set(dataItem, value);
                        }
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 强制设置实体位移向量
     */
    public static void forceSetDeltaMovement(LivingEntity entity, double x, double y, double z) {
        entity.setDeltaMovement(x, y, z);
    }

    /**
     * 强制移除实体 - 完整实现
     * 对标omnimobs的forceRemove，包括setRemoved和onRemoved
     */
    public static void forceRemove(LivingEntity entity, Object removalReason) {
        // 方法1：运行所有remove相关方法
        runRemoveMethods(entity);
        
        // 方法2：强制设置removed标志
        forceSetRemoved(entity);
        
        // 方法3：调用onRemovedFromWorld
        try {
            entity.onRemovedFromWorld();
        } catch (Exception e) {
            com.Harbinger.Spore.Spore.LOGGER.error("onRemovedFromWorld failed", e);
        }
        
        // 方法4：最后调用discard
        entity.discard();
        
        com.Harbinger.Spore.Spore.LOGGER.info("[强制移除] 实体: " + entity.getClass().getSimpleName() + 
            " UUID: " + entity.getUUID());
    }
    
    /**
     * 运行所有remove相关方法
     * 对标omnimobs的runRemoveMethods
     */
    private static void runRemoveMethods(net.minecraft.world.entity.Entity entity) {
        try {
            // 遍历所有方法
            for (java.lang.reflect.Method method : entity.getClass().getDeclaredMethods()) {
                method.setAccessible(true);
                String methodName = method.getName().toLowerCase();
                
                // 检查方法名是否包含set、start且与remove相关
                if ((methodName.contains("set") || methodName.contains("start")) && 
                    (methodName.contains("remove") || methodName.contains("discard") || 
                     methodName.contains("kill") || methodName.contains("unregister"))) {
                    try {
                        // 调用无参方法
                        if (method.getParameterCount() == 0) {
                            method.invoke(entity);
                        }
                    } catch (Exception e) {
                        com.Harbinger.Spore.Spore.LOGGER.debug("[EntityUtil] runRemoveMethods invoke ignored: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            com.Harbinger.Spore.Spore.LOGGER.error("runRemoveMethods failed", e);
        }
    }
    
    /**
     * 强制设置removed标志
     * 对标omnimobs的forceSetRemoved
     */
    private static void forceSetRemoved(net.minecraft.world.entity.Entity entity) {
        try {
            // 尝试设置removed字段
            java.lang.reflect.Field removedField = net.minecraft.world.entity.Entity.class.getDeclaredField("f_146795");
            removedField.setAccessible(true);
            removedField.set(entity, net.minecraft.world.entity.Entity.RemovalReason.KILLED);
        } catch (Exception e) {
            // 尝试其他可能的字段名
            try {
                for (java.lang.reflect.Field field : entity.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    String fieldName = field.getName().toLowerCase();
                    if (fieldName.contains("removed") || fieldName.contains("f_146795")) {
                        if (field.getType() == net.minecraft.world.entity.Entity.RemovalReason.class) {
                            field.set(entity, net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                        }
                    }
                }
            } catch (Exception e2) {
                // 忽略
            }
        }
    }

    /**
     * 获取指定范围内的所有实体
     */
    public static List<LivingEntity> entityList(float range, Level level, double x, double y, double z) {
        try {
            List<LivingEntity> entities = new ArrayList<>();
            net.minecraft.world.phys.AABB aabb = new net.minecraft.world.phys.AABB(
                x - range, y - range, z - range,
                x + range, y + range, z + range
            );
            
            level.getEntitiesOfClass(LivingEntity.class, aabb, entities::add);
            return entities;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 强制设置实体位置
     */
    public static void forceSetPos(LivingEntity entity, double x, double y, double z) {
        entity.moveTo(x, y, z);
    }

    /**
     * 强制攻击击退
     * 根据击退强度和强制击退标志计算击退效果
     */
    public static void forceAttackKnockback(LivingEntity attacker, LivingEntity target, float knockback, boolean forceKnockback) {
        double d0 = attacker.getX() - target.getX();
        double d1 = attacker.getZ() - target.getZ();
        
        // 防止击退向量过小
        while (d0 * d0 + d1 * d1 < 1.0E-4) {
            d0 = (Math.random() - Math.random()) * 0.01;
            d1 = (Math.random() - Math.random()) * 0.01;
        }
        
        if (forceKnockback) {
            forceKnockback(target, 0.4 * knockback, d0, d1, true);
        } else {
            forceKnockback(target, 0.4 * knockback, d0, d1, false);
        }
    }

    /**
     * 强制击退核心方法
     * 支持忽略击退抗性
     */
    public static void forceKnockback(LivingEntity entity, double x, double y, double z, boolean ignoreKnockbackResistance) {
        x = ignoreKnockbackResistance ? (x *= 1.0) : (x *= 1.0 - Mth.clamp(entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 0.9));
        
        if (x <= 0.0) {
            return;
        }
        
        Vec3 vec3 = entity.getDeltaMovement();
        Vec3 vec31 = new Vec3(y, 0.0, z).normalize().scale(x);
        
        entity.setDeltaMovement(
            vec3.x / 2.0 - vec31.x,
            entity.isNoGravity() ? Math.min(0.4, vec3.y / 2.0 + x) : vec3.y,
            vec3.z / 2.0 - vec31.z
        );
    }

    /**
     * 添加带击退抗性的位移
     */
    public static void addDeltaMovementWithKnockbackResistance(LivingEntity entity, double x, double y, double z) {
        double resistance = 1.0 - Mth.clamp(entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 0.9);
        entity.setDeltaMovement(entity.getDeltaMovement().add(x * resistance, y, z * resistance));
    }

    /**
     * 强制获取实体数据字段
     */
    public static Object forceGetEntityData(Object entityData, Object accessor) {
        try {
            java.lang.reflect.Field dataField = entityData.getClass().getDeclaredField("value");
            dataField.setAccessible(true);
            return dataField.get(entityData);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取所有实体
     */
    public static Object getAllEntities(Level level) {
        // 预留接口，需要使用正确的API获取所有实体
        return Collections.emptyList();
    }

    /**
     * 检测实体是否真的活着
     * 包括NaN血量、无穷血量等情况
     */
    public static boolean actuallyAlive(LivingEntity entity) {
        return entity.isAlive() || entity.getHealth() > 0.0f ||
               Float.isNaN(entity.getHealth()) ||
               entity.getHealth() == Float.POSITIVE_INFINITY;
    }
}