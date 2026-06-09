package com.Harbinger.Spore.coremod;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Method;

/**
 * 拦截 LivingEntity.hurt(DamageSource, float)，
 * 注入 HurtPatcherMethodVisitor 实现无敌帧硬闸和伤害限制。
 * 应用于所有 LivingEntity 子类。
 */
public class HurtPatcherClassVisitor extends ClassVisitor {
    private final String className;
    private final String simpleName;

    /** 从 loaded class 反射解析的 hurt() 方法名（兼容 MCP/SRG） */
    private static final String HURT_NAME;

    static {
        String name = null;
        try {
            Class<?> checkClass = LivingEntity.class;
            outer:
            while (checkClass != null && checkClass != Object.class) {
                for (Method m : checkClass.getDeclaredMethods()) {
                    if (m.getReturnType() == boolean.class
                            && m.getParameterCount() == 2
                            && m.getParameterTypes()[0] == DamageSource.class
                            && m.getParameterTypes()[1] == float.class
                            && !m.isBridge()) {
                        name = m.getName();
                        break outer;
                    }
                }
                checkClass = checkClass.getSuperclass();
            }
        } catch (Exception e) {
            System.err.println("[SporeCore] HurtPatcher: failed to resolve hurt() name: " + e);
        }
        HURT_NAME = name;
        System.out.println("[SporeCore] HurtPatcher: HURT_NAME resolved to '" + name + "'");
    }

    /** Class<?> → String 委托（供老调用方使用） */
    public HurtPatcherClassVisitor(ClassVisitor classVisitor, Class<?> c) {
        this(classVisitor, c != null ? c.getName() : "unknown");
    }

    /** 供 ModLauncher plugin 使用（加载时无 Class 引用） */
    public HurtPatcherClassVisitor(ClassVisitor classVisitor, String className) {
        super(Opcodes.ASM9, classVisitor);
        this.className = className;
        int dot = className != null ? className.lastIndexOf('.') : -1;
        this.simpleName = dot >= 0 ? className.substring(dot + 1) : className;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (HURT_NAME == null) {
            // HURT_NAME 未解析成功，跳过变换
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        // 名称匹配 + 返回 boolean（不以 descriptor 中的类型名为准，兼容 SRG/notch 映射）
        if (name.equals(HURT_NAME) && descriptor.endsWith(")Z")) {
            System.out.println("[SporeCore] HurtPatcher: ✓ patching " + this.className + "." + name + descriptor);
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv != null) {
                return new HurtPatcherMethodVisitor(mv);
            }
        } else {
            // 调试：打印目标类的所有 boolean 返回方法（HURT_NAME 未匹配时）
            if (descriptor.endsWith(")Z") && this.className != null) {
                System.out.println("[SporeCore] HurtPatcher: candidate in " + this.simpleName + ": " + name + descriptor + " (HURT_NAME='" + HURT_NAME + "')");
            }
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }
}
