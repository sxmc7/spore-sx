package com.Harbinger.Spore.Sentities.anticheat;

import com.Harbinger.Spore.Sentities.anticheat.AccessChecker;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;

public class ProtectedWeakHashMap<K, V>
extends WeakHashMap<K, V> {
    public ProtectedWeakHashMap() {
    }

    public ProtectedWeakHashMap(ProtectedWeakHashMap<K, V> healthValues) {
        super(healthValues);
    }

    public V remove(Object key) {
        if (!AccessChecker.checkAccess()) {
            return (V)this.get(key);
        }
        return (V)super.remove(key);
    }

    public boolean remove(Object key, Object value) {
        if (!AccessChecker.checkAccess()) {
            return false;
        }
        return super.remove(key, value);
    }

    public boolean replace(K key, V oldValue, V newValue) {
        if (!AccessChecker.checkAccess()) {
            return false;
        }
        return super.replace(key, oldValue, newValue);
    }

    public V put(K key, V value) {
        if (!AccessChecker.checkAccess()) {
            return value;
        }
        return (V)super.put(key, value);
    }

    public V putIfAbsent(K key, V value) {
        if (!AccessChecker.checkAccess()) {
            return value;
        }
        return (V)super.putIfAbsent(key, value);
    }

    public V replace(K key, V value) {
        if (!AccessChecker.checkAccess()) {
            return value;
        }
        return (V)super.replace(key, value);
    }

    public void clear() {
        if (!AccessChecker.checkAccess()) {
            return;
        }
        super.clear();
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        if (!AccessChecker.checkAccess()) {
            return;
        }
        super.putAll(m);
    }

    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        if (!AccessChecker.checkAccess()) {
            return;
        }
        super.replaceAll(function);
    }

}