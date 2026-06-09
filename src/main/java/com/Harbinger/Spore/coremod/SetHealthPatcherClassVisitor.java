package com.Harbinger.Spore.coremod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 拦截 LivingEntity.setHealth(float)，
 * 注入 SetHealthPatcherMethodVisitor 实现改血限伤。
 * 注意：此变换应用于所有 LivingEntity（含 Spore 实体），
 * 保护逻辑在 CoreModHooks.limitSetHealth() 中按包名判断。
 */
public class SetHealthPatcherClassVisitor extends ClassVisitor {
    private final String className;

    private static final String SET_HEALTH_NAME;

    static {
        String name = null;
        try {
            name = ObfuscationReflectionHelper.findMethod(
                    LivingEntity.class, "m_21154_", float.class).getName();
        } catch (Exception e) {
            System.err.println("[SporeCore] SetHealthPatcher: failed to resolve setHealth() SRG name: " + e);
        }
        SET_HEALTH_NAME = name;
    }

    /** Class<?> → String 委托（供老调用方使用） */
    public SetHealthPatcherClassVisitor(ClassVisitor classVisitor, Class<?> c) {
        this(classVisitor, c != null ? c.getName() : "unknown");
    }

    /** 供 ModLauncher plugin 使用 */
    public SetHealthPatcherClassVisitor(ClassVisitor classVisitor, String className) {
        super(Opcodes.ASM9, classVisitor);
        this.className = className;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (SET_HEALTH_NAME != null
                && name.equals(SET_HEALTH_NAME)
                && descriptor.equals("(F)V")) {
            System.out.println("[SporeCore] SetHealthPatcher: patching setHealth for " + this.className);
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv != null) {
                return new SetHealthPatcherMethodVisitor(mv);
            }
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }
}
