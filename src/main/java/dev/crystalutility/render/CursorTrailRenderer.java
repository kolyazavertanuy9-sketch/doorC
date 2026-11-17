package dev.crystalutility.render;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
import java.util.Iterator;

public final class CursorTrailRenderer {
    private static final long TRAIL_LIFETIME_NANOS = 380_000_000L;
    private static final double SAMPLE_SPACING = 2.0;
    private static final ArrayDeque<Point> POINTS = new ArrayDeque<>();
    private static Object currentScreen;

    private CursorTrailRenderer() {
    }

    public static void render(Object screen, GuiGraphics graphics, int mouseX, int mouseY, int accent) {
        long now = System.nanoTime();
        if (screen != currentScreen) {
            POINTS.clear();
            currentScreen = screen;
        }

        if (mouseX < 0 || mouseY < 0 || mouseX >= graphics.guiWidth() || mouseY >= graphics.guiHeight()) {
            POINTS.clear();
            return;
        }

        Point last = POINTS.peekLast();
        if (last == null || Math.hypot(mouseX - last.x, mouseY - last.y) >= 1.0) {
            POINTS.addLast(new Point(mouseX, mouseY, now));
        } else {
            POINTS.removeLast();
            POINTS.addLast(new Point(last.x, last.y, now));
        }

        while (!POINTS.isEmpty() && now - POINTS.peekFirst().time > TRAIL_LIFETIME_NANOS) {
            POINTS.removeFirst();
        }

        if (POINTS.size() < 2) return;
        Iterator<Point> iterator = POINTS.iterator();
        Point from = iterator.next();
        while (iterator.hasNext()) {
            Point to = iterator.next();
            drawSegment(graphics, from, to, now, accent);
            from = to;
        }
        Point tip = POINTS.peekLast();
        drawDot(graphics, tip.x, tip.y, 3, 0xBFFFFFFF);
    }

    private static void drawSegment(GuiGraphics graphics, Point from, Point to, long now, int accent) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double length = Math.hypot(dx, dy);
        int steps = Math.max(1, (int) Math.ceil(length / SAMPLE_SPACING));
        for (int step = 0; step <= steps; step++) {
            double fraction = (double) step / steps;
            int x = (int) Math.round(from.x + dx * fraction);
            int y = (int) Math.round(from.y + dy * fraction);
            long time = from.time + Math.round((to.time - from.time) * fraction);
            float age = Math.max(0.0f, Math.min(1.0f,
                    (float) (now - time) / TRAIL_LIFETIME_NANOS));
            float strength = 1.0f - age;
            int glowAlpha = Math.round(0x24 * strength);
            int lineAlpha = Math.round(0xCE * strength);
            drawDot(graphics, x, y, 7, withAlpha(accent, glowAlpha));
            drawDot(graphics, x, y, 2, withAlpha(accent, lineAlpha));
        }
    }

    private static void drawDot(GuiGraphics graphics, int centerX, int centerY, int diameter, int color) {
        SmoothHudRenderer.drawRoundedRect(graphics, centerX - diameter / 2, centerY - diameter / 2,
                diameter, diameter, diameter / 2, color);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha & 0xFF) << 24;
    }

    private record Point(int x, int y, long time) {
    }
}
