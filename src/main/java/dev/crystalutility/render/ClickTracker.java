package dev.crystalutility.render;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ClickTracker {
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final Deque<Long> LEFT_CLICKS = new ArrayDeque<>();
    private static final Deque<Long> RIGHT_CLICKS = new ArrayDeque<>();

    private ClickTracker() {}

    public static synchronized void record(int button) {
        Deque<Long> clicks = button == 0 ? LEFT_CLICKS : button == 1 ? RIGHT_CLICKS : null;
        if (clicks == null) return;
        long now = System.nanoTime();
        prune(clicks, now);
        clicks.addLast(now);
    }

    public static synchronized int cps(int button) {
        long now = System.nanoTime();
        Deque<Long> clicks = button == 0 ? LEFT_CLICKS : button == 1 ? RIGHT_CLICKS : null;
        if (clicks == null) return 0;
        prune(clicks, now);
        return clicks.size();
    }

    private static void prune(Deque<Long> clicks, long now) {
        while (!clicks.isEmpty() && now - clicks.peekFirst() >= WINDOW_NANOS) {
            clicks.removeFirst();
        }
    }
}
