package com.Harbinger.Spore.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Spore模组调试命令
 * 用于检查限伤系统和百分比伤害系统的运行状态
 */
public class SporeDebugCommands {
    
    /**
     * 注册调试命令
     */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 限伤系统状态命令
        dispatcher.register(Commands.literal("spore")
            .then(Commands.literal("damage-limit")
                .then(Commands.literal("status")
                    .executes(SporeDebugCommands::showDamageLimitStatus))
                .then(Commands.literal("test")
                    .requires(source -> source.hasPermission(2))
                    .executes(SporeDebugCommands::testDamageLimit))
            )
            // 百分比伤害系统状态命令
            .then(Commands.literal("percentage-damage")
                .then(Commands.literal("status")
                    .executes(SporeDebugCommands::showPercentageDamageStatus))
                .then(Commands.literal("test")
                    .requires(source -> source.hasPermission(2))
                    .executes(SporeDebugCommands::testPercentageDamage))
            )
            // 配置查看命令
            .then(Commands.literal("config")
                .executes(SporeDebugCommands::showAllConfig))
        );
    }
    
    /**
     * 显示限伤系统状态
     */
    private static int showDamageLimitStatus(CommandContext<CommandSourceStack> context) {
        try {
            String status = com.Harbinger.Spore.Sentities.anticheat.DamageLimiter.getSystemStatus();
            context.getSource().sendSuccess(() -> Component.literal(status), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("获取限伤系统状态失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 测试限伤系统
     */
    private static int testDamageLimit(CommandContext<CommandSourceStack> context) {
        try {
            context.getSource().sendSuccess(() -> Component.literal("限伤系统测试功能已触发"), false);
            context.getSource().sendSuccess(() -> Component.literal(com.Harbinger.Spore.Sentities.anticheat.DamageLimiter.getSystemStatus()), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("测试失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 显示百分比伤害系统状态
     */
    private static int showPercentageDamageStatus(CommandContext<CommandSourceStack> context) {
        try {
            String status = com.Harbinger.Spore.util.SporeDamageDispatcher.getSystemStatus();
            context.getSource().sendSuccess(() -> Component.literal(status), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("获取百分比伤害系统状态失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 测试百分比伤害系统
     */
    private static int testPercentageDamage(CommandContext<CommandSourceStack> context) {
        try {
            context.getSource().sendSuccess(() -> Component.literal("百分比伤害系统测试功能已触发"), false);
            context.getSource().sendSuccess(() -> Component.literal(com.Harbinger.Spore.util.SporeDamageDispatcher.getSystemStatus()), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("测试失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 显示所有配置
     */
    private static int showAllConfig(CommandContext<CommandSourceStack> context) {
        try {
            StringBuilder config = new StringBuilder();
            config.append("=== Spore模组配置 ===\n\n");
            config.append(com.Harbinger.Spore.Sentities.anticheat.DamageLimiter.getSystemStatus());
            config.append("\n");
            config.append(com.Harbinger.Spore.util.SporeDamageDispatcher.getSystemStatus());
            
            context.getSource().sendSuccess(() -> Component.literal(config.toString()), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("获取配置失败: " + e.getMessage()));
            return 0;
        }
    }
}
