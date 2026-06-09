package com.Harbinger.Spore.Sentities.anticheat;

import com.Harbinger.Spore.Sentities.anticheat.StackChecker;
import java.security.SecureRandom;
import java.util.Arrays;

public class AccessChecker {
    private static final byte[] baseValue;
    private static final ThreadLocal<byte[]> tempValue;

    /**
     * 执行特权操作，需要在受保护的上下文中调用
     */
    public static void performPrivilegedAction(Runnable runnable) {
        if (StackChecker.notCalledFromAllowedPackage(1, 5, true)) {
            return;
        }
        tempValue.set(baseValue);
        try {
            runnable.run();
        }
        finally {
            tempValue.remove();
        }
    }

    /**
     * 检查访问权限 - 简化版本，只检查堆栈跟踪
     * 移除了tempValue检查，因为限伤系统不需要特权操作
     * 高频缓存：同一线程 100ms 内返回上次结果
     */
    public static boolean checkAccess() {
        try {
            // 只检查堆栈跟踪是否来自允许的包（带高频缓存）
            if (StackChecker.notCalledFromAllowedPackageCached(1, 5, true)) {
                return false;
            }
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查访问权限 - 完整版本，包含特权操作验证
     * 用于需要高级安全保护的场景
     */
    public static boolean checkAccessStrict() {
        try {
            if (StackChecker.notCalledFromAllowedPackage(1, 5, true)) {
                return false;
            }
            if (baseValue == null) {
                return false;
            }
            if (tempValue.get() == null) {
                return false;
            }
            return Arrays.compare(tempValue.get(), baseValue) == 0;
        }
        catch (Exception e) {
            return false;
        }
    }

    static {
        tempValue = new ThreadLocal();
        SecureRandom secureRandom = new SecureRandom();
        baseValue = secureRandom.generateSeed(128);
    }
}