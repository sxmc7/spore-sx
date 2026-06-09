package com.Harbinger.Spore.coremod;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class HealPatcherMethodVisitor extends MethodVisitor {
    public HealPatcherMethodVisitor(MethodVisitor mv) {
        super(Opcodes.ASM9, mv);
    }

    @Override
    public void visitCode() {
        super.visitCode();
        // Inject: if (CoreModHooks.shouldBlockHeal(this)) return;
        mv.visitVarInsn(Opcodes.ALOAD, 0);  // this
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/Harbinger/Spore/coremod/CoreModHooks",
                "shouldBlockHeal",
                "(Lnet/minecraft/world/entity/LivingEntity;)Z",
                false);
        Label label = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, label);  // if false → continue normal heal
        mv.visitInsn(Opcodes.RETURN);            // block heal
        mv.visitLabel(label);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
    }
}
