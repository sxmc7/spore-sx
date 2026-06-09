package com.Harbinger.Spore.coremod;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

public class SporeClassVisitor extends ClassVisitor {

    private static final String TICK_SERVER_NAME;
    private static final String RENDER_LEVEL_NAME;

    static {
        String tick = null;
        String render = null;
        try {
            tick = ObfuscationReflectionHelper.findMethod(
                    MinecraftServer.class, "m_5705_", BooleanSupplier.class).getName();
        } catch (Exception e) {
            System.err.println("[SporeCore] SporeClassVisitor: failed to resolve tickServer SRG: " + e);
        }
        try {
            render = ObfuscationReflectionHelper.findMethod(
                    LevelRenderer.class, "m_109599_",
                    com.mojang.blaze3d.vertex.PoseStack.class, Float.TYPE, Long.TYPE,
                    Boolean.TYPE, Camera.class, GameRenderer.class, LightTexture.class, Matrix4f.class).getName();
        } catch (Exception e) {
            System.err.println("[SporeCore] SporeClassVisitor: failed to resolve renderLevel SRG: " + e);
        }
        TICK_SERVER_NAME = tick;
        RENDER_LEVEL_NAME = render;
    }

    public SporeClassVisitor(ClassVisitor classVisitor) {
        super(589824, classVisitor);
    }

    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (TICK_SERVER_NAME != null && name.equals(TICK_SERVER_NAME)
                && descriptor.equals("(Ljava/util/function/BooleanSupplier;)V")) {
            System.out.println("[SporeCore] SporeClassVisitor: patching tickServer");
            MethodVisitor visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(589824, visitor) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String descriptor, boolean isInterface) {
                    super.visitMethodInsn(opcode, owner, methodName, descriptor, isInterface);
                    if (opcode == 184 && owner.equals("net/minecraftforge/event/ForgeEventFactory")
                            && methodName.equals("onPreServerTick")) {
                        mv.visitMethodInsn(184, "com/Harbinger/Spore/coremod/CoreModHooks",
                                "tickServer", "()V", false);
                    }
                }
            };
        }
        if (RENDER_LEVEL_NAME != null && name.equals(RENDER_LEVEL_NAME)) {
            System.out.println("[SporeCore] SporeClassVisitor: patching renderLevel");
            MethodVisitor visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(589824, visitor) {
                private int count = 0;
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String descriptor, boolean isInterface) {
                    super.visitMethodInsn(opcode, owner, methodName, descriptor, isInterface);
                    if (opcode == 184 && owner.equals("net/minecraftforge/client/ForgeHooksClient")
                            && methodName.equals("dispatchRenderStage")) {
                        if (this.count == 1) {
                            mv.visitVarInsn(25, 1);
                            mv.visitVarInsn(25, 6);
                            mv.visitMethodInsn(184, "com/Harbinger/Spore/coremod/CoreModHooks",
                                    "renderAfterEntities",
                                    "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Camera;)V", false);
                        }
                        ++this.count;
                    }
                }
            };
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }
}
