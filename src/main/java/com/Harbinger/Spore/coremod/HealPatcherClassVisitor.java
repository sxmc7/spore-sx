package com.Harbinger.Spore.coremod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class HealPatcherClassVisitor extends ClassVisitor {
    private final String className;

    public HealPatcherClassVisitor(ClassVisitor classVisitor, Class<?> c) {
        this(classVisitor, c != null ? c.getName() : "unknown");
    }

    public HealPatcherClassVisitor(ClassVisitor classVisitor, String className) {
        super(Opcodes.ASM9, classVisitor);
        this.className = className;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name.equals(ObfuscationReflectionHelper.findMethod(LivingEntity.class, "m_21153_", float.class).getName())) {
            System.out.println("[SporeCore] HealPatcher: visiting heal method for " + this.className);
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (methodVisitor != null) {
                return new HealPatcherMethodVisitor(methodVisitor);
            }
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }
}
