package dev.wvlib.api;

public final class ThemeColors {
    private static final int DEFAULT_BACKGROUND = 0xFF090B12;
    private static final int DEFAULT_ACCENT = 0xFF3478F6;

    private ThemeColors() {}

    public static int accent(String color) {
        return parseColor(color, DEFAULT_ACCENT);
    }

    public static int background(String color) {
        return parseColor(color, DEFAULT_BACKGROUND);
    }

    public static int background(String color, int opacity) {
        int argb = background(color);
        int alpha = ((argb >>> 24) & 0xFF) * (opacity & 0xFF) / 0xFF;
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    private static int parseColor(String value, int fallback) {
        if (value == null) return fallback;
        String hex = value.trim().replaceFirst("^#", "");
        if (hex.length() == 6) hex = "FF" + hex;
        if (hex.length() != 8) return fallback;
        try {
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
