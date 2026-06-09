package com.Harbinger.Spore.coremod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 拦截 LivingEntity.travel(Vec3) 和 Mob.serverAiStep()，
 * 注入 FreezePatcherMethodVisitor 实现时停冻结。
 */
public class FreezePatcherClassVisitor extends ClassVisitor {
    private final String className;

    private static final String TRAVEL_NAME;
    private static final String SERVER_AI_STEP_NAME;

    static {
        String travel = null;
        String serverAiStep = null;
        try {
            travel = ObfuscationReflectionHelper.findMethod(LivingEntity.class, "m_6471_", Vec3.class).getName();
        } catch (Exception e) {
            System.err.println("[SporeCore] FreezePatcher: failed to resolve travel() SRG name: " + e);
        }
        try {
            serverAiStep = ObfuscationReflectionHelper.findMethod(Mob.class, "m_8020_", new Class[0]).getName();
        } catch (Exception e) {
            System.err.println("[SporeCore] FreezePatcher: failed to resolve serverAiStep() SRG name: " + e);
        }
        TRAVEL_NAME = travel;
        SERVER_AI_STEP_NAME = serverAiStep;
    }

    public FreezePatcherClassVisitor(ClassVisitor classVisitor, Class<?> c) {
        this(classVisitor, c != null ? c.getName() : "unknown");
    }

    public FreezePatcherClassVisitor(ClassVisitor classVisitor, String className) {
        super(Opcodes.ASM9, classVisitor);
        this.className = className;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        boolean matchTravel = TRAVEL_NAME != null && name.equals(TRAVEL_NAME) && descriptor.equals("(Lnet/minecraft/world/phys/Vec3;)V");
        boolean matchAiStep = SERVER_AI_STEP_NAME != null && name.equals(SERVER_AI_STEP_NAME) && descriptor.equals("()V");

        if (matchTravel || matchAiStep) {
            System.out.println("[SporeCore] FreezePatcher: patching " + (matchTravel ? "travel" : "serverAiStep")
                    + " for " + this.className);
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv != null) {
                return new FreezePatcherMethodVisitor(mv);
            }
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }
}
