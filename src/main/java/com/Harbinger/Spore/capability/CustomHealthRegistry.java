package com.Harbinger.Spore.capability;

import com.Harbinger.Spore.Spore;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 自定义血量 Capability 注册表。
 *
 * 职责：
 * 1. 向 Forge 注册 ICustomHealth 能力
 * 2. 仅为 Spore LivingEntity 附加 Capability Provider（非 Spore 生物不附着）
 * 3. Spore 生物 active=true，原 livingentity.health 字段成为诱饵
 *
 * 注意：RegisterCapabilitiesEvent 走 MOD 总线，
 * AttachCapabilitiesEvent 走 FORGE 总线（在 Spore.java 中注册）。
 */
@Mod.EventBusSubscriber(modid = Spore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CustomHealthRegistry {
    private static final ResourceLocation CAP_KEY = new ResourceLocation(Spore.MODID, "custom_health");

    /** Capability 实例 — Forge 1.20.1 使用 CapabilityManager 替代 @CapabilityInject */
    public static final Capability<ICustomHealth> CUSTOM_HEALTH = CapabilityManager.get(new CapabilityToken<>() {});

    @SubscribeEvent
    public static void registerCaps(RegisterCapabilitiesEvent event) {
        event.register(ICustomHealth.class);
        Spore.LOGGER.info("[CustomHealth] Capability registered");
    }

    /**
     * 在 Spore.java 中注册到 FORGE 事件总线：
     * MinecraftForge.EVENT_BUS.addListener(CustomHealthRegistry::onAttachCapabilities);
     */
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof LivingEntity entity)) return;
        // 仅 Spore 生物附着 Capability，非 Spore 生物不受任何影响
        if (!entity.getClass().getName().startsWith("com.Harbinger.Spore.")) return;

        CustomHealthCapability cap = new CustomHealthCapability();
        cap.setActive(true);
        // 注意：此时实体正在 Entity.<init>() 中，health 字段尚未初始化（默认 0）
        // getMaxHealth() 也可能因 attributes 未就绪而报错
        // 所以全部使用 safe default，由后续的 setHealth/mixin 纠正
        float maxHp = 20.0f;
        try { maxHp = entity.getMaxHealth(); } catch (Exception ignored) {}
        cap.setCustomMaxHealth(maxHp);
        cap.setCustomHealth(maxHp);
        Spore.LOGGER.info("[CustomHealth] Attached to Spore entity: {} hp={}",
                entity.getClass().getSimpleName(), maxHp);

        event.addCapability(CAP_KEY, new ICapabilitySerializable<CompoundTag>() {
            private final LazyOptional<ICustomHealth> lazy = LazyOptional.of(() -> cap);

            @Override
            public @NotNull <T> LazyOptional<T> getCapability(
                    @NotNull Capability<T> capability, @Nullable Direction side) {
                return capability == CUSTOM_HEALTH ? lazy.cast() : LazyOptional.empty();
            }

            @Override
            public CompoundTag serializeNBT() {
                CompoundTag tag = new CompoundTag();
                tag.putFloat("Health", cap.getCustomHealth());
                tag.putFloat("MaxHealth", cap.getCustomMaxHealth());
                tag.putBoolean("Active", cap.isActive());
                return tag;
            }

            @Override
            public void deserializeNBT(CompoundTag tag) {
                cap.setCustomHealth(tag.getFloat("Health"));
                cap.setCustomMaxHealth(tag.getFloat("MaxHealth"));
                cap.setActive(tag.getBoolean("Active"));
            }
        });
    }
}
