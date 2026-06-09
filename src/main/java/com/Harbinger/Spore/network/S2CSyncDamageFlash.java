package com.Harbinger.Spore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 同步受伤闪光效果到客户端
 */
public class S2CSyncDamageFlash {
    private final int flashTime;
    private final int entityId;

    public S2CSyncDamageFlash(int flashTime, int entityId) {
        this.flashTime = flashTime;
        this.entityId = entityId;
    }

    public static void encode(S2CSyncDamageFlash msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.flashTime);
        buffer.writeInt(msg.entityId);
    }

    public static S2CSyncDamageFlash decode(FriendlyByteBuf buffer) {
        return new S2CSyncDamageFlash(buffer.readInt(), buffer.readInt());
    }

    public static void handle(S2CSyncDamageFlash msg, NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            // 客户端渲染受伤闪光效果
        });
        ctx.setPacketHandled(true);
    }
}