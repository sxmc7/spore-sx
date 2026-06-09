package com.Harbinger.Spore.coremod;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.util.EnumSet;
import java.util.Set;

/**
 * ModLauncher ILaunchPluginService — 加载时字节码变换。
 *
 * 在类加载到 JVM 之前完成变换，无需 Java Agent / Attach API / tools.jar。
 * 兼容 Android/Termux 环境。
 */
public class SporeLaunchPlugin implements ILaunchPluginService {

    public static boolean ENABLED = false;

    private static final Set<String> TARGET_CLASSES = Set.of(
            "net.minecraft.world.entity.LivingEntity",
            "net.minecraft.world.entity.Mob",
            "net.minecraft.server.MinecraftServer",
            "net.minecraft.client.renderer.LevelRenderer"
    );

    private static final String[] ENTITY_BASE = {
            "net.minecraft.world.entity.LivingEntity",
            "net.minecraft.world.entity.Mob"
    };

    @Override
    public String name() {
        return "spore_core";
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        return EnumSet.of(Phase.AFTER);
    }

    @Override
    public int processClassWithFlags(Phase phase, ClassNode classNode, Type classType, String className) {
        if (phase != Phase.AFTER) return ComputeFlags.NO_REWRITE;
        if (!TARGET_CLASSES.contains(className)) return ComputeFlags.NO_REWRITE;

        System.out.println("[SporeLaunchPlugin] 变换类: " + className);

        try {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            classNode.accept(cw);
            byte[] bytes = cw.toByteArray();

            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw2 = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

            ClassVisitor visitor = cw2;
            String[] entityBases = ENTITY_BASE;

            boolean isEntity = false;
            for (String base : entityBases) {
                if (base.equals(className)) {
                    isEntity = true;
                    break;
                }
            }

            if (isEntity) {
                visitor = new HurtPatcherClassVisitor(visitor, className);
                visitor = new SetHealthPatcherClassVisitor(visitor, className);
                visitor = new HealPatcherClassVisitor(visitor, className);
                visitor = new FreezePatcherClassVisitor(visitor, className);
            } else {
                visitor = new SporeClassVisitor(visitor);
            }

            cr.accept(visitor, ClassReader.EXPAND_FRAMES);
            byte[] transformed = cw2.toByteArray();

            ClassNode result = new ClassNode();
            new ClassReader(transformed).accept(result, 0);
            copyTo(classNode, result);

            ENABLED = true;
            System.out.println("[SporeLaunchPlugin] ✓ 完成: " + className);
            return ComputeFlags.SIMPLE_REWRITE;

        } catch (Exception e) {
            System.err.println("[SporeLaunchPlugin] 失败: " + className + ": " + e.getMessage());
            e.printStackTrace();
            return ComputeFlags.NO_REWRITE;
        }
    }

    /** 将变换后的 ClassNode 内容复制回原节点 */
    private static void copyTo(ClassNode target, ClassNode src) {
        target.version = src.version;
        target.access = src.access;
        target.name = src.name;
        target.signature = src.signature;
        target.superName = src.superName;
        target.interfaces = src.interfaces;
        target.sourceFile = src.sourceFile;
        target.sourceDebug = src.sourceDebug;
        target.outerClass = src.outerClass;
        target.outerMethod = src.outerMethod;
        target.outerMethodDesc = src.outerMethodDesc;
        target.fields = src.fields;
        target.methods = src.methods;
        target.innerClasses = src.innerClasses;
        target.visibleAnnotations = src.visibleAnnotations;
        target.invisibleAnnotations = src.invisibleAnnotations;
        target.visibleTypeAnnotations = src.visibleTypeAnnotations;
        target.invisibleTypeAnnotations = src.invisibleTypeAnnotations;
    }
}
