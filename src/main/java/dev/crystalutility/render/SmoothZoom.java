package dev.crystalutility.render;

public final class SmoothZoom {
    private static final float MAX_ZOOM = 3.0f;
    private static final double RESPONSE_SECONDS = 0.09;
    private static float amount;
    private static long lastUpdateNanos;

    private SmoothZoom() {}

    public static float update(boolean active) {
        long now = System.nanoTime();
        if (lastUpdateNanos == 0) {
            lastUpdateNanos = now;
            return 1.0f;
        }

        double elapsed = Math.min(0.1, Math.max(0.0, (now - lastUpdateNanos) / 1_000_000_000.0));
        lastUpdateNanos = now;
        float target = active ? 1.0f : 0.0f;
        float response = (float) (1.0 - Math.exp(-elapsed / RESPONSE_SECONDS));
        amount += (target - amount) * response;
        amount = Math.max(0.0f, Math.min(1.0f, amount));
        return 1.0f + (MAX_ZOOM - 1.0f) * amount;
    }
}
