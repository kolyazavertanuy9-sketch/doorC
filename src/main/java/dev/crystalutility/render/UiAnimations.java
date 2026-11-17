package dev.crystalutility.render;

import java.util.HashMap;
import java.util.Map;

public final class UiAnimations {
    private static final long ENTRY_RESET_NANOS = 300_000_000L;
    private static final long ENTRY_DURATION_NANOS = 240_000_000L;
    private static final Map<Object, ValueState> VALUES = new java.util.WeakHashMap<>();
    private static final Map<Object, EntryState> ENTRIES = new HashMap<>();

    private UiAnimations() {
    }

    public static float approach(Object key, float target, float initial, long durationNanos) {
        long now = System.nanoTime();
        ValueState state = VALUES.computeIfAbsent(key, ignored -> new ValueState(initial, now));
        float elapsed = Math.min(0.1f, (now - state.updatedAt) / 1_000_000_000.0f);
        state.updatedAt = now;
        if (durationNanos <= 0) {
            state.value = target;
        } else {
            float amount = 1.0f - (float) Math.exp(-elapsed * 1_000_000_000.0 / durationNanos);
            state.value += (target - state.value) * amount;
        }
        return state.value;
    }

    public static float entryProgress(Object key) {
        long now = System.nanoTime();
        EntryState state = ENTRIES.get(key);
        if (state == null || now - state.lastSeen > ENTRY_RESET_NANOS) {
            state = new EntryState(now, now);
            ENTRIES.put(key, state);
        } else {
            state.lastSeen = now;
        }
        float progress = Math.min(1.0f, (float) (now - state.startedAt) / ENTRY_DURATION_NANOS);
        return progress * progress * (3.0f - 2.0f * progress);
    }

    private static final class ValueState {
        private float value;
        private long updatedAt;

        private ValueState(float value, long updatedAt) {
            this.value = value;
            this.updatedAt = updatedAt;
        }
    }

    private static final class EntryState {
        private final long startedAt;
        private long lastSeen;

        private EntryState(long startedAt, long lastSeen) {
            this.startedAt = startedAt;
            this.lastSeen = lastSeen;
        }
    }
}
