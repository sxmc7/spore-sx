package com.Harbinger.Spore.Sentities.anticheat;

import java.util.Random;
import java.util.WeakHashMap;

/**
 * Spore 实体独立血量存储 — 多槽旋转编码 + WeakHashMap。
 *
 * 概念：将真实血量从原版字段分散到编码槽中，防止通过
 * 反射/Unsafe/内存扫描定位真实血量。
 *
 * 与加速器模组的区别（各自独立实现）：
 *           加速器                              Spore
 *   ├ 16 槽 + Long.reverse + XOR    ├ 8 槽 + Integer.rotate + XOR
 *   ├ WeakHashMap<Object>           ├ WeakHashMap<Object>（同思路不同编码）
 *   ├ 堆外 Unsafe.allocateMemory    ├ 纯 Java 堆
 *   ├ 每次写清全部 16 槽随机化      ├ 每次写只换 1 槽 + 扰 3 槽
 *   └ HealthType.CURRENT/MAX 双维   └ 仅 CURRENT 单维
 *
 * 存储结构（每实体独立）：
 *   8 槽 long[] + 随机 activeIndex + 随机 key
 *   编码：float²int → XOR key → Integer.rotateRight(7)
 *  WeakHashMap 自动 GC，无需手动 register/unregister。
 */
public class SporeHealthStorage {

    private static final int SLOT_COUNT = 8;
    /** 写入时额外扰动的槽位数 */
    private static final int SCRAMBLE_SLOTS = 3;

    /** 实体对象 → 血量数据（WeakHashMap 自动 GC） */
    private static final WeakHashMap<Object, HealthData> STORAGE = new WeakHashMap<>();

    private static final class HealthData {
        final long[] slots = new long[SLOT_COUNT];
        int activeIndex;
        int key;
        final Random rng = new Random();

        HealthData(float health) {
            rng.setSeed((long) (health * 1000) ^ System.nanoTime());
            activeIndex = rng.nextInt(SLOT_COUNT);
            key = rng.nextInt();
            for (int i = 0; i < SLOT_COUNT; i++) {
                slots[i] = i == activeIndex ? encode(health, key) : rng.nextLong();
            }
        }

        float read() {
            int bits = decode(slots[activeIndex], key);
            float val = Float.intBitsToFloat(bits);
            return Float.isNaN(val) ? 0f : Math.max(0f, val);
        }

        void write(float health) {
            activeIndex = rng.nextInt(SLOT_COUNT);
            key = rng.nextInt();
            for (int i = 0; i < SCRAMBLE_SLOTS; i++) {
                int idx = rng.nextInt(SLOT_COUNT);
                if (idx != activeIndex) slots[idx] = rng.nextLong();
            }
            slots[activeIndex] = encode(health, key);
        }

        /** float → 编码 long */
        private static long encode(float health, int key) {
            return Integer.toUnsignedLong(
                Integer.rotateRight(Float.floatToRawIntBits(health) ^ key, 7));
        }

        /** 编码 long → float */
        private static int decode(long raw, int key) {
            return Integer.rotateLeft((int) raw, 7) ^ key;
        }
    }

    // ======== API（所有读写都接收实体对象，WeakHashMap 自动生命周期）========

    /** 读取血量。若未注册则返回 0。 */
    public static float getHealth(Object entity) {
        synchronized (STORAGE) {
            HealthData data = STORAGE.get(entity);
            return data != null ? data.read() : 0f;
        }
    }

    /** 写入血量。若未注册则自动创建。 */
    public static void setHealth(Object entity, float health) {
        health = Math.max(0f, health);
        synchronized (STORAGE) {
            HealthData data = STORAGE.get(entity);
            if (data == null) {
                data = new HealthData(health);
                STORAGE.put(entity, data);
            } else {
                data.write(health);
            }
        }
    }
}
