package com.Harbinger.Spore.util;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Spore命令注册器
 * 负责注册所有Spore模组的调试和管理命令
 */
@Mod.EventBusSubscriber(modid = "spore")
public class SporeCommandRegistrar {
    
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // 注册所有调试命令
        SporeDebugCommands.registerCommands(event.getDispatcher());
    }
}