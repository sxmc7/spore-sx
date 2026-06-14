package com.Harbinger.Spore.util;

import com.Harbinger.Spore.Spore;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

/**
 * JNI 桥接 — 提供字段操作和方法调用能力。
 * JNI 调用不产生 Java 栈帧，可绕过 StackWalker 检查。
 */
public class SporeNativeBridge {
    private static boolean loaded = false;
    private static boolean available = false;

    public static boolean isAvailable() { return available; }

    private static final String LIB_NAME = "spore_jni";
    private static final String[] FALLBACK_PATHS = {
        "/storage/emulated/0/FCL/.minecraft/versions/1.20.1-Forge/natives/libspore_jni.so",
        "/data/data/com.termux/files/home/spore_native/libspore_jni.so",
        "/storage/emulated/0/spore_native/libspore_jni.so"
    };

    public static void init() {
        if (loaded) return;
        loaded = true;

        // 1. System.loadLibrary（搜 java.library.path，和 hyper_spore 相同方式）
        try {
            System.loadLibrary(LIB_NAME);
            available = true;
            Spore.LOGGER.info("[JNI] Loaded via loadLibrary");
            return;
        } catch (Throwable ignored) {}

        // 2. 从 jar 资源提取到临时文件
        try {
            InputStream in = SporeNativeBridge.class.getResourceAsStream("/native/lib" + LIB_NAME + ".so");
            if (in != null) {
                File tmp = File.createTempFile("spore_jni_", ".so");
                tmp.deleteOnExit();
                OutputStream out = new FileOutputStream(tmp);
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                out.close();
                in.close();
                System.load(tmp.getAbsolutePath());
                available = true;
                Spore.LOGGER.info("[JNI] Loaded from jar resource: " + tmp);
                return;
            }
        } catch (Throwable ignored) {}

        // 3. 外部路径 fallback
        for (String path : FALLBACK_PATHS) {
            try {
                File f = new File(path);
                if (f.exists() && f.canRead()) {
                    System.load(path);
                    available = true;
                    Spore.LOGGER.info("[JNI] Loaded from: " + path);
                    return;
                }
            } catch (Throwable ignored) {}
        }

        Spore.LOGGER.info("[JNI] Native bridge not available (optional)");
    }

    // ===== Native 方法 =====

    /** 设 int 字段（通过字段名，搜索整个类层次） */
    private static native boolean nSetInt(Object target, String fieldName, int value);

    /** 设 boolean 字段 */
    private static native boolean nSetBool(Object target, String fieldName, boolean value);

    /** 调用 hurt(DamageSource, float) — 当前线程 */
    private static native boolean nCallHurt(Object entity, Object source, float amount);

    /** 调用 setHealth(float) — 当前线程 */
    private static native boolean nCallSetHealth(Object entity, float health);

    /** 调用 hurt — 新线程执行（干净栈，绕过 StackGuard） */
    private static native boolean nCleanHurt(Object entity, Object source, float amount);

    /** 调用 setHealth — 新线程执行（干净栈） */
    private static native boolean nCleanSetHealth(Object entity, float health);

    // ===== Java 公开 API =====

    /** 清除目标实体所有可能的无敌帧/锁字段，然后循环 hurt */
    public static boolean adaptiveDamage(LivingEntity entity, DamageSource source, float damage, int maxAttempts) {
        if (!available) return false;

        for (int i = 0; i < maxAttempts; i++) {
            if (!entity.isAlive()) return true;
            clearProtectionFields(entity);
            nCallHurt(entity, source, damage);
        }
        return !entity.isAlive();
    }

    /** 干净线程版（绕过 StackGuard） */
    public static boolean cleanAdaptiveDamage(LivingEntity entity, DamageSource source, float damage, int maxAttempts) {
        if (!available) return false;

        for (int i = 0; i < maxAttempts; i++) {
            if (!entity.isAlive()) return true;
            clearProtectionFields(entity);
            nCleanHurt(entity, source, damage);
        }
        return !entity.isAlive();
    }

    /** 仅通过 JNI 调用目标的 hurt()，不清除任何字段 */
    public static boolean hurtViaNative(LivingEntity entity, DamageSource source, float damage) {
        if (!available) return false;
        return nCallHurt(entity, source, damage);
    }

    /** 干净线程版 hurt()（无 Spore 栈帧，绕过 StackChecker） */
    public static boolean cleanHurtViaNative(LivingEntity entity, DamageSource source, float damage) {
        if (!available) return false;
        return nCleanHurt(entity, source, damage);
    }

    /** 清除目标疑似保护字段：int ∈ [1,200] 归零，boolean=true 置 false */
    public static void clearProtectionFields(Object entity) {
        if (!available) return;
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (java.lang.reflect.Modifier.isFinal(f.getModifiers())) continue;
                    Class<?> type = f.getType();
                    if (type == int.class) {
                        f.setAccessible(true);
                        int val = f.getInt(entity);
                        if (val >= 1 && val <= 200) {
                            nSetInt(entity, f.getName(), 0);
                        }
                    } else if (type == boolean.class) {
                        f.setAccessible(true);
                        if (f.getBoolean(entity)) {
                            nSetBool(entity, f.getName(), false);
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ignored) {}
    }

    /** 直接设 setHealth（当前线程，适用于无 StackGuard 的目标） */
    public static boolean setHealth(LivingEntity entity, float health) {
        if (!available) return false;
        return nCallSetHealth(entity, health);
    }

    /** 干净线程 setHealth（绕过 StackGuard） */
    public static boolean cleanSetHealth(LivingEntity entity, float health) {
        if (!available) return false;
        return nCleanSetHealth(entity, health);
    }
}
