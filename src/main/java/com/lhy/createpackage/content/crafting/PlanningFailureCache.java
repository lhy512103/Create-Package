package com.lhy.createpackage.content.crafting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

/** Short, bounded negative cache. Hits do not extend the retry deadline. */
final class PlanningFailureCache<K, S> {
    private final Map<K, Entry<S>> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final int capacity;
    private final int retryTicks;

    PlanningFailureCache(int capacity, int retryTicks) {
        this.capacity = capacity;
        this.retryTicks = retryTicks;
    }

    @Nullable
    String get(K key, S state, long now) {
        Entry<S> entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (now < entry.started() || now - entry.started() >= retryTicks || !Objects.equals(state, entry.state())) {
            entries.remove(key);
            return null;
        }
        return entry.reason();
    }

    void remember(K key, S state, long now, String reason) {
        entries.put(key, new Entry<>(state, now, reason));
        if (entries.size() > capacity) {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    private record Entry<S>(S state, long started, String reason) {
    }
}
