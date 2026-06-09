package com.Harbinger.Spore.coremod;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 在 setHealth(float) 开头注入 health 参数修改：
 *   health = CoreModHooks.limitSetHealth(this, health);
 * 为 Spore 实体提供字节码级改血防护。
 */
public class SetHealthPatcherMethodVisitor extends MethodVisitor {
    public SetHealthPatcherMethodVisitor(MethodVisitor mv) {
        super(Opcodes.ASM9, mv);
    }

    @Override
    public void visitCode() {
        super.visitCode();
        // health = CoreModHooks.limitSetHealth(this, health);
        mv.visitVarInsn(Opcodes.ALOAD, 0);    // this
        mv.visitVarInsn(Opcodes.FLOAD, 1);    // health 参数
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/Harbinger/Spore/coremod/CoreModHooks",
                "limitSetHealth",
                "(Lnet/minecraft/world/entity/LivingEntity;F)F",
                false);
        mv.visitVarInsn(Opcodes.FSTORE, 1);   // 将修改后的值存回 health
    }
}
