package com.Harbinger.Spore.coremod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 拦截 LivingEntity.getHealth()，
 * 注入 GetHealthPatcherMethodVisitor 防止返回 0 导致假死。
 */
public class GetHealthPatcherClassVisitor extends ClassVisitor {
    private final String className;

    private static final String GET_HEALTH_NAME;

    static {
        String name = null;
        try {
            name = ObfuscationReflectionHelper.findMethod(
                    LivingEntity.class, "m_21223_").getName();
            System.out.println("[SporeCore] GetHealthPatcher: resolved getHealth SRG -> '" + name + "'");
        } catch (Exception e) {
            System.err.println("[SporeCore] GetHealthPatcher: SRG m_21223_ failed: " + e);
            // Fallback: 尝试直接用 MojMap 名 (Forge 1.20.1 运行时用 MojMap)
            try {
                LivingEntity.class.getMethod("getHealth");
                name = "getHealth";
                System.out.println("[SporeCore] GetHealthPatcher: fallback to 'getHealth'");
            } catch (Exception e2) {
                System.err.println("[SporeCore] GetHealthPatcher: fallback also failed: " + e2);
            }
        }
        GET_HEALTH_NAME = name;
        System.out.println("[SporeCore] GetHealthPatcher: final method name = '" + GET_HEALTH_NAME + "'");
    }

    public GetHealthPatcherClassVisitor(ClassVisitor classVisitor, String className) {
        super(Opcodes.ASM9, classVisitor);
        this.className = className;
        System.out.println("[SporeCore] GetHealthPatcher: created visitor for " + className +
                " (target method: " + GET_HEALTH_NAME + ")");
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (descriptor.equals("()F")) {
            System.out.println("[SporeCore] GetHealthPatcher: checking method '" + name + "' ()F in " + this.className);
        }
        if (GET_HEALTH_NAME != null
                && name.equals(GET_HEALTH_NAME)
                && descriptor.equals("()F")) {
            System.out.println("[SporeCore] GetHealthPatcher: ★ PATCHING getHealth for " + this.className);
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv != null) {
                return new GetHealthPatcherMethodVisitor(mv);
            }
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }
}
