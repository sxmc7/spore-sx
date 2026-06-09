package com.Harbinger.Spore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 同步Boss血量到客户端
 */
public class S2CSyncBossHealth {
    private final float health;
    private final int entityId;

    public S2CSyncBossHealth(float health, int entityId) {
        this.health = health;
        this.entityId = entityId;
    }

    public static void encode(S2CSyncBossHealth msg, FriendlyByteBuf buffer) {
        buffer.writeFloat(msg.health);
        buffer.writeInt(msg.entityId);
    }

    public static S2CSyncBossHealth decode(FriendlyByteBuf buffer) {
        return new S2CSyncBossHealth(buffer.readFloat(), buffer.readInt());
    }

    public static void handle(S2CSyncBossHealth msg, NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            // 这里需要实际同步血量的逻辑
        });
        ctx.setPacketHandled(true);
    }
}