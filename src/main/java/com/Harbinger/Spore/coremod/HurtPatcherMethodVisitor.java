package com.Harbinger.Spore.coremod;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Label;

/**
 * 修改 LivingEntity.hurt(DamageSource, float)Z 方法字节码：
 *   1. visitCode() 最开头插入真实无敌帧硬闸 — 若 checkRealInvuln(this) 为真则无条件 return false
 *   2. visitCode() 中保留现有限伤系统初始化（注册实体 + 重置历史）
 *   3. visitInsn(IRETURN) 前插入 onHurtReturn(this, result) 以设置无敌帧 NBT
 *   4. visitInsn(IRETURN) 前保留现有 DamageLimiter.limitDamage 调用
 */
public class HurtPatcherMethodVisitor extends MethodVisitor {

    public HurtPatcherMethodVisitor(MethodVisitor mv) {
        super(Opcodes.ASM9, mv);
    }

    @Override
    public void visitCode() {
        // === LAYER 1: HARD GATE — 真实无敌帧检查（在所有其他逻辑之前） ===
        // if (CoreModHooks.checkRealInvuln(this)) return false;
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/Harbinger/Spore/coremod/CoreModHooks",
                "checkRealInvuln",
                "(Lnet/minecraft/world/entity/LivingEntity;)Z",
                false);
        Label proceed = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, proceed);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitLabel(proceed);
        // MixinClassWriter(COMPUTE_FRAMES) 自动处理栈帧

        // === 现有逻辑：限伤系统初始化 ===
        // 注册实体到限伤系统
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/Harbinger/Spore/Sentities/anticheat/DamageLimiter",
                "isRegistered",
                "(Lnet/minecraft/world/entity/LivingEntity;)Z",
                false);
        Label isRegistered = new Label();
        mv.visitJumpInsn(Opcodes.IFNE, isRegistered);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/Harbinger/Spore/Sentities/anticheat/DamageLimiter",
                "registerEntity",
                "(Lnet/minecraft/world/entity/LivingEntity;)V",
                false);
        mv.visitLabel(isRegistered);

        // 重置伤害历史
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/Harbinger/Spore/Sentities/anticheat/DamageLimiter",
                "resetDamageHistory",
                "(Lnet/minecraft/world/entity/LivingEntity;)V",
                false);

        // 保存原始伤害值到局部变量 10
        mv.visitVarInsn(Opcodes.FSTORE, 10);

        super.visitCode();
    }

    @Override
    public void visitInsn(int opcode) {
        // 仅在 IRETURN（hurt 返回 boolean）时插入钩子
        if (opcode == Opcodes.IRETURN) {
            // === LAYER 2: onHurtReturn — 记录伤害结果并设置无敌帧 ===
            // 返回值（boolean 在栈顶，bytecode 中为 int）
            mv.visitInsn(Opcodes.DUP);               // 复制返回值
            mv.visitVarInsn(Opcodes.ISTORE, 11);     // 存入 var 11
            mv.visitVarInsn(Opcodes.ALOAD, 0);       // this
            mv.visitVarInsn(Opcodes.ILOAD, 11);      // result
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "com/Harbinger/Spore/coremod/CoreModHooks",
                    "onHurtReturn",
                    "(Lnet/minecraft/world/entity/LivingEntity;Z)V",
                    false);
            // 注意：不加载 var 11 — 返回值已由前面的 DUP 保留在栈上

            // === 现有逻辑：DamageLimiter.limitDamage 调用 ===
            mv.visitVarInsn(Opcodes.ALOAD, 0);       // this
            mv.visitVarInsn(Opcodes.ALOAD, 1);       // DamageSource
            mv.visitVarInsn(Opcodes.FLOAD, 10);      // 原始伤害值
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "com/Harbinger/Spore/Sentities/anticheat/DamageLimiter",
                    "limitDamage",
                    "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F",
                    false);
            mv.visitVarInsn(Opcodes.FSTORE, 2);      // 存回 amount 参数
        }

        super.visitInsn(opcode);
    }
}
