package dev.crystalutility.render;

import dev.crystalutility.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;

public final class HudLayout {
    private HudLayout() {}

    public enum Widget {
        COORDINATES, KEYSTROKES, CPS, ITEMS, ARMOR, EFFECTS, MEDIA, TARGET, REACH, COMBO
    }

    public static int x(ModConfig config, Widget widget, int screenWidth, int screenHeight) {
        int stored = storedX(config, widget);
        int fallback = switch (widget) {
            case COORDINATES, KEYSTROKES -> 10;
            case CPS -> 10;
            case ITEMS, ARMOR -> (screenWidth - width(config, widget)) / 2;
            case EFFECTS, REACH -> screenWidth - width(config, widget) - 10;
            case MEDIA, TARGET, COMBO -> (screenWidth - width(config, widget)) / 2;
        };
        return clamp(stored < 0 ? fallback : stored, 0,
                Math.max(0, screenWidth - width(config, widget)));
    }

    public static int y(ModConfig config, Widget widget, int screenWidth, int screenHeight) {
        int stored = storedY(config, widget);
        int keystrokesY = screenHeight - 30 - height(config, Widget.KEYSTROKES)
                - (config.cpsHudEnabled ? height(config, Widget.CPS) + 5 : 0);
        int fallback = switch (widget) {
            case COORDINATES, EFFECTS -> 10;
            case KEYSTROKES -> keystrokesY;
            case ITEMS -> config.armorHudEnabled
                    ? screenHeight - 24 - height(config, widget) - height(config, Widget.ARMOR) - 6
                    : screenHeight - height(config, widget) - 24;
            case CPS -> config.keystrokesHudEnabled
                    ? keystrokesY + height(config, Widget.KEYSTROKES) + 5
                    : screenHeight - height(config, widget) - 30;
            case ARMOR -> screenHeight - height(config, widget) - 24;
            case MEDIA -> 12;
            case TARGET -> screenHeight / 2 + 25;
            case REACH -> 62;
            case COMBO -> screenHeight / 2 + 68;
        };
        return clamp(stored < 0 ? fallback : stored, 0, Math.max(0, screenHeight - height(config, widget)));
    }

    public static void set(ModConfig config, Widget widget, int x, int y) {
        switch (widget) {
            case COORDINATES -> { config.hudCoordinatesX = x; config.hudCoordinatesY = y; }
            case KEYSTROKES -> { config.hudKeystrokesX = x; config.hudKeystrokesY = y; }
            case CPS -> { config.hudCpsX = x; config.hudCpsY = y; }
            case ITEMS -> { config.hudInventoryItemsX = x; config.hudInventoryItemsY = y; }
            case ARMOR -> { config.hudArmorX = x; config.hudArmorY = y; }
            case EFFECTS -> { config.hudEffectsX = x; config.hudEffectsY = y; }
            case MEDIA -> { config.hudMediaX = x; config.hudMediaY = y; }
            case TARGET -> { config.hudTargetX = x; config.hudTargetY = y; }
            case REACH -> { config.hudReachX = x; config.hudReachY = y; }
            case COMBO -> { config.hudComboX = x; config.hudComboY = y; }
        }
    }

    public static int width(Widget widget) {
        return switch (widget) {
            case COORDINATES -> 220;
            case KEYSTROKES -> 80;
            case CPS -> 100;
            case ITEMS -> 6 * 43 + 5 * 4;
            case ARMOR -> 102;
            case EFFECTS -> 142;
            case MEDIA -> 286;
            case TARGET -> 150;
            case REACH -> 84;
            case COMBO -> 100;
        };
    }

    public static int width(ModConfig config, Widget widget) {
        return Math.round(width(widget) * scale(config, widget));
    }

    public static int height(ModConfig config, Widget widget) {
        return Math.round(baseHeight(config, widget) * scale(config, widget));
    }

    public static float scale(ModConfig config, Widget widget) {
        return switch (widget) {
            case COORDINATES -> config.hudCoordinatesScale;
            case KEYSTROKES -> config.hudKeystrokesScale;
            case CPS -> config.hudCpsScale;
            case ITEMS -> config.hudInventoryItemsScale;
            case ARMOR -> config.hudArmorScale;
            case EFFECTS -> config.hudEffectsScale;
            case MEDIA -> config.hudMediaScale;
            case TARGET -> config.hudTargetScale;
            case REACH -> config.hudReachScale;
            case COMBO -> config.hudComboScale;
        };
    }

    public static void setScale(ModConfig config, Widget widget, float scale) {
        float bounded = Float.isFinite(scale) ? Math.max(0.5f, Math.min(2.0f, scale)) : 1.0f;
        switch (widget) {
            case COORDINATES -> config.hudCoordinatesScale = bounded;
            case KEYSTROKES -> config.hudKeystrokesScale = bounded;
            case CPS -> config.hudCpsScale = bounded;
            case ITEMS -> config.hudInventoryItemsScale = bounded;
            case ARMOR -> config.hudArmorScale = bounded;
            case EFFECTS -> config.hudEffectsScale = bounded;
            case MEDIA -> config.hudMediaScale = bounded;
            case TARGET -> config.hudTargetScale = bounded;
            case REACH -> config.hudReachScale = bounded;
            case COMBO -> config.hudComboScale = bounded;
        }
    }

    public static void withScale(
            GuiGraphics graphics, ModConfig config, Widget widget,
            int screenWidth, int screenHeight, Runnable render) {
        int x = x(config, widget, screenWidth, screenHeight);
        int y = y(config, widget, screenWidth, screenHeight);
        float progress = UiAnimations.entryProgress(widget);
        float baseScale = scale(config, widget);
        float animatedScale = baseScale * (0.96f + 0.04f * progress);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().translate(0, (1.0f - progress) * 7.0f);
            graphics.pose().scale(animatedScale, animatedScale);
            graphics.pose().translate(-x, -y);
            render.run();
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static int baseHeight(ModConfig config, Widget widget) {
        return switch (widget) {
            case COORDINATES -> 44;
            case KEYSTROKES -> 94;
            case CPS -> 30;
            case ITEMS -> 28;
            case ARMOR -> 40;
            case EFFECTS -> 18 + 5 * 24;
            case MEDIA -> 60;
            case TARGET -> 36;
            case REACH -> 26;
            case COMBO -> 28;
        };
    }

    private static int storedX(ModConfig config, Widget widget) {
        return switch (widget) {
            case COORDINATES -> config.hudCoordinatesX;
            case KEYSTROKES -> config.hudKeystrokesX;
            case CPS -> config.hudCpsX;
            case ITEMS -> config.hudInventoryItemsX;
            case ARMOR -> config.hudArmorX;
            case EFFECTS -> config.hudEffectsX;
            case MEDIA -> config.hudMediaX;
            case TARGET -> config.hudTargetX;
            case REACH -> config.hudReachX;
            case COMBO -> config.hudComboX;
        };
    }

    private static int storedY(ModConfig config, Widget widget) {
        return switch (widget) {
            case COORDINATES -> config.hudCoordinatesY;
            case KEYSTROKES -> config.hudKeystrokesY;
            case CPS -> config.hudCpsY;
            case ITEMS -> config.hudInventoryItemsY;
            case ARMOR -> config.hudArmorY;
            case EFFECTS -> config.hudEffectsY;
            case MEDIA -> config.hudMediaY;
            case TARGET -> config.hudTargetY;
            case REACH -> config.hudReachY;
            case COMBO -> config.hudComboY;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
