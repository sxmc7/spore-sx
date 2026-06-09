package com.Harbinger.Spore.network;

import com.Harbinger.Spore.Spore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Spore网络包处理器
 * 主要用于百分比伤害系统和限伤系统的网络同步
 */
public class PacketHandler {
    
    public static void register() {
        // 网络包注册（预留接口）
        // 实际的网络包注册需要使用正确的Forge API
    }

    public static void sendToClient(Object message) {
        // 发送到客户端（预留接口）
        // 需要使用Forge网络API
    }
    
    public static void sendToClient(Object message, ServerPlayer player) {
        // 发送到特定客户端（预留接口）
    }
    
    public static void sendToServer(Object message) {
        // 发送到服务器（预留接口）
    }
}