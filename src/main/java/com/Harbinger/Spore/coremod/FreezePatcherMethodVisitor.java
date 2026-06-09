package com.Harbinger.Spore.coremod;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 在方法开头注入：if (CoreModHooks.isTimeStopped(this)) return;
 * 用于 travel(Vec3) 和 serverAiStep() 两个方法，实现时停冻结。
 */
public class FreezePatcherMethodVisitor extends MethodVisitor {
    public FreezePatcherMethodVisitor(MethodVisitor mv) {
        super(Opcodes.ASM9, mv);
    }

    @Override
    public void visitCode() {
        super.visitCode();
        // if (CoreModHooks.isTimeStopped(this)) return;
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/Harbinger/Spore/coremod/CoreModHooks",
                "isTimeStopped",
                "(Lnet/minecraft/world/entity/LivingEntity;)Z",
                false);
        Label label = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, label);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitLabel(label);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
    }
}
