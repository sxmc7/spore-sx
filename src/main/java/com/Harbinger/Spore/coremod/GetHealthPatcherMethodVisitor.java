package com.Harbinger.Spore.coremod;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 在 getHealth() 的每个 FRETURN 前注入：
 *   returnValue = CoreModHooks.guardGetHealth(this, returnValue);
 * 防止外部 Unsafe 直写 DataItem 导致 getHealth() 返回 0 触发 tickDeath。
 */
public class GetHealthPatcherMethodVisitor extends MethodVisitor {
    public GetHealthPatcherMethodVisitor(MethodVisitor mv) {
        super(Opcodes.ASM9, mv);
    }

    @Override
    public void visitInsn(int opcode) {
        if (opcode == Opcodes.FRETURN) {
            // stack: [float returnValue]
            mv.visitVarInsn(Opcodes.ALOAD, 0);       // stack: [float, this]
            mv.visitInsn(Opcodes.SWAP);               // stack: [this, float]
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "com/Harbinger/Spore/coremod/CoreModHooks",
                    "guardGetHealth",
                    "(Lnet/minecraft/world/entity/LivingEntity;F)F",
                    false);
            // stack: [float result]
        }
        super.visitInsn(opcode);
    }
}
