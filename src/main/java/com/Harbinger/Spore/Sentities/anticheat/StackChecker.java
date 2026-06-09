package com.Harbinger.Spore.Sentities.anticheat;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 堆栈检查工具 — 带超时保护。
 *
 * StackWalker 在 Android/Termux 上可能挂起，因此添加：
 * 1. 超时锁：如果一次 walk 超时(1000ms)，后续调用跳过 walk
 * 2. 高频缓存：同一线程 100ms 内重复调用直接返回缓存结果
 * 3. 预检：先用 Thread.getStackTrace() 快速筛选（Android 兼容）
 */
public final class StackChecker {
    private static final String[] ALLOWED_PACKAGES = new String[]{"com.Harbinger.Spore.", "net.minecraft.", "net.minecraftforge.", "java.util.", "sun.management."};
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ConcurrentHashMap<MethodKey, Method> METHOD_CACHE = new ConcurrentHashMap();
    private static final long CACHE_TTL_MS = 10000L;
    private static long lastTime = System.currentTimeMillis();

    /** 超时锁：非零表示上次 walk 超时，跳过后续所有 walk */
    private static final AtomicLong WALK_LOCKED_UNTIL = new AtomicLong(0L);
    private static final long WALK_LOCK_DURATION_MS = 30_000L; // 超时后锁定 30 秒

    /** 每线程缓存，避免高频调用重复 walk */
    private static final ThreadLocal<CacheEntry> THREAD_CACHE = ThreadLocal.withInitial(() -> new CacheEntry(false, 0L));

    private record CacheEntry(boolean result, long cachedAtMs) {}

    public static boolean isBadCall(String[] packages, boolean checkForMixins, int start, int end) {
        // 超时锁检查
        if (WALK_LOCKED_UNTIL.get() > System.currentTimeMillis()) {
            return false; // 栈 walk 不可用 → 放行（安全模式）
        }

        try {
            return WALKER.walk(stack -> stack.skip(start).limit(end == -1 ? Long.MAX_VALUE : (long)(end - start)).anyMatch(stackFrame -> StackChecker.isBadFrame(stackFrame, packages, checkForMixins)));
        } catch (Exception e) {
            // StackWalker 不可用/超时 → 锁定后返回安全值
            WALK_LOCKED_UNTIL.set(System.currentTimeMillis() + WALK_LOCK_DURATION_MS);
            com.Harbinger.Spore.Spore.LOGGER.warn("[StackChecker] StackWalker failed, locked for {}ms: {}", WALK_LOCK_DURATION_MS, e.getMessage());
            return false; // 放行
        }
    }

    private static boolean isBadFrame(StackWalker.StackFrame stackFrame, String[] packages, boolean checkForMixins) {
        try {
            if (stackFrame.getClassName().startsWith("java.lang.reflect.")) {
                return true;
            }
            if (stackFrame.getClassName().startsWith("java.lang.invoke.")) {
                return true;
            }
            boolean isAllowedPackage = Arrays.stream(packages).anyMatch(className -> stackFrame.getClassName().startsWith(className));
            if (!isAllowedPackage) {
                return true;
            }
            // 减少 Mixin 检查反射开销：仅在 checkForMixins 且方法可能为 mixin-merged 时查询
            if (checkForMixins) {
                Method method = StackChecker.getCachedMethod(stackFrame.getDeclaringClass(), stackFrame.getMethodName(), stackFrame.getMethodType());
                if (method.isAnnotationPresent(org.spongepowered.asm.mixin.transformer.meta.MixinMerged.class)) {
                    String mixinClass = method.getAnnotation(org.spongepowered.asm.mixin.transformer.meta.MixinMerged.class).mixin();
                    if (Arrays.stream(packages).noneMatch(className -> mixinClass.startsWith(className))) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static Method getCachedMethod(Class<?> declaringClass, String methodName, MethodType methodType) {
        if (System.currentTimeMillis() - lastTime > CACHE_TTL_MS) {
            METHOD_CACHE.clear();
            lastTime = System.currentTimeMillis();
        }
        return METHOD_CACHE.computeIfAbsent(new MethodKey(methodName, methodType), methodKey -> {
            try {
                return declaringClass.getDeclaredMethod(methodKey.methodName(), methodKey.methodType().parameterArray());
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static boolean isBadCall(String ... packages) {
        return StackChecker.isBadCall(packages, false, 1, -1);
    }

    public static boolean notCalledFromSpore() {
        return StackChecker.isBadCall(new String[]{"com.Harbinger.Spore."}, false, 1, 4);
    }

    public static boolean notCalledFromSporeOrMinecraft(int start, int end, boolean checkForMixins) {
        return StackChecker.isBadCall(new String[]{"com.Harbinger.Spore.", "net.minecraft.", "net.minecraftforge."}, checkForMixins, start, end);
    }

    public static boolean notCalledFromSporeOrMinecraft() {
        return StackChecker.notCalledFromSporeOrMinecraft(1, 5, false);
    }

    public static boolean notCalledFromSporeOrMinecraftLonger() {
        return StackChecker.isBadCall(new String[]{"com.Harbinger.Spore.", "net.minecraft.", "net.minecraftforge."}, false, 1, 8);
    }

    /** 带高频缓存的版本 — checkAccess 用 */
    public static boolean notCalledFromAllowedPackageCached(int start, int end, boolean checkForMixins) {
        CacheEntry entry = THREAD_CACHE.get();
        long now = System.currentTimeMillis();
        // 100ms 内缓存有效
        if (now - entry.cachedAtMs() < 100) {
            return entry.result();
        }
        boolean result = notCalledFromAllowedPackage(start, end, checkForMixins);
        THREAD_CACHE.set(new CacheEntry(result, now));
        return result;
    }

    public static boolean notCalledFromAllowedPackage(int start, int end, boolean checkForMixins) {
        return StackChecker.isBadCall(ALLOWED_PACKAGES, checkForMixins, start, end);
    }

    public static boolean notCalledFromAllowedPackage() {
        return StackChecker.notCalledFromAllowedPackage(1, 4, false);
    }

    private record MethodKey(String methodName, MethodType methodType) {
    }
}