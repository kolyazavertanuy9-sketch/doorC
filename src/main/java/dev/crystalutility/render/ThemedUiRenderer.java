package dev.crystalutility.render;

import dev.crystalutility.config.ModConfig;
import dev.wvlib.api.ThemeColors;
import dev.crystalutility.text.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;

import java.util.Map;
import java.util.WeakHashMap;

public final class ThemedUiRenderer {
    private static final int BACKGROUND_WIDTH = 1344;
    private static final int BACKGROUND_HEIGHT = 768;
    private static final long BACKGROUND_HOLD_NANOS = 18_000_000_000L;
    private static final long BACKGROUND_FADE_NANOS = 1_600_000_000L;
    private static final long BACKGROUND_STARTED_AT = System.nanoTime();
    private static final Identifier[] TITLE_BACKGROUNDS = {
            Identifier.fromNamespaceAndPath("crystalutility", "textures/gui/backgrounds/world_1.png"),
            Identifier.fromNamespaceAndPath("crystalutility", "textures/gui/backgrounds/world_2.png"),
            Identifier.fromNamespaceAndPath("crystalutility", "textures/gui/backgrounds/world_3.png"),
            Identifier.fromNamespaceAndPath("crystalutility", "textures/gui/backgrounds/world_4.png")
    };
    private static final Map<AbstractButton, HoverState> BUTTON_HOVERS = new WeakHashMap<>();

    private ThemedUiRenderer() {}

    public static void drawButton(GuiGraphics graphics, AbstractButton button) {
        ModConfig config = ModConfig.get();
        int accent = ThemeColors.accent(config.uiAccentColor);
        float hover = hoverAmount(button, button.isActive() && button.isHoveredOrFocused());
        int background = withAlpha(ThemeColors.background(config.uiBackgroundColor, 0xD4),
                Math.round(0xD4 * ModConfig.UI_PANEL_OPACITY));
        int border = withAlpha(accent, Math.round(0x28 + hover * 0x9A));
        int fill = withAlpha(mix(background, withAlpha(accent, 0xA0), 0.22f * hover),
                Math.round((0xD4 + hover * 0x2B) * ModConfig.UI_PANEL_OPACITY));

        int x = button.getX();
        int y = button.getY();
        int right = button.getRight();
        int bottom = button.getBottom();
        fillRounded(graphics, x, y, right - x, bottom - y, 11, border);
        fillRounded(graphics, x + 1, y + 1, right - x - 2, bottom - y - 2, 10, fill);
        SmoothHudRenderer.drawRoundedRect(graphics, x + 12, y + 2, right - x - 24, 1, 1,
                withAlpha(0xFFFFFFFF, Math.round(0x14 + hover * 0x24)));
    }

    public static void drawTitleBackground(GuiGraphics graphics, Screen screen) {
        long elapsed = Math.max(0L, System.nanoTime() - BACKGROUND_STARTED_AT);
        long slide = elapsed / BACKGROUND_HOLD_NANOS;
        long phase = elapsed % BACKGROUND_HOLD_NANOS;
        int current = (int) (slide % TITLE_BACKGROUNDS.length);
        boolean fading = phase >= BACKGROUND_HOLD_NANOS - BACKGROUND_FADE_NANOS;
        if (fading) {
            float progress = (float) (phase - (BACKGROUND_HOLD_NANOS - BACKGROUND_FADE_NANOS))
                    / BACKGROUND_FADE_NANOS;
            drawBackgroundImage(graphics, screen, TITLE_BACKGROUNDS[current],
                    Math.round(255 * (1.0f - progress)));
            int next = (current + 1) % TITLE_BACKGROUNDS.length;
            drawBackgroundImage(graphics, screen, TITLE_BACKGROUNDS[next],
                    Math.round(255 * progress));
        } else {
            drawBackgroundImage(graphics, screen, TITLE_BACKGROUNDS[current], 255);
        }

        graphics.fill(0, 0, screen.width, screen.height, 0x1C050B14);
        graphics.fillGradient(0, 0, screen.width, screen.height / 2,
                0x3006101C, 0x0006101C);
        graphics.fillGradient(0, screen.height / 2, screen.width, screen.height,
                0x00080C18, 0x4C060D18);
    }

    public static void drawScreenBackdrop(GuiGraphics graphics, Screen screen, int worldScrimColor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            drawTitleBackground(graphics, screen);
            graphics.blurBeforeThisStratum();
            return;
        }

        graphics.blurBeforeThisStratum();
        graphics.fill(0, 0, screen.width, screen.height, worldScrimColor);
    }

    private static void drawBackgroundImage(
            GuiGraphics graphics, Screen screen, Identifier texture, int alpha) {
        float screenAspect = (float) screen.width / Math.max(1, screen.height);
        float imageAspect = (float) BACKGROUND_WIDTH / BACKGROUND_HEIGHT;
        float u = 0;
        float v = 0;
        int sourceWidth = BACKGROUND_WIDTH;
        int sourceHeight = BACKGROUND_HEIGHT;
        if (screenAspect > imageAspect) {
            sourceHeight = Math.round(BACKGROUND_WIDTH / screenAspect);
            v = (BACKGROUND_HEIGHT - sourceHeight) / 2.0f;
        } else {
            sourceWidth = Math.round(BACKGROUND_HEIGHT * screenAspect);
            u = (BACKGROUND_WIDTH - sourceWidth) / 2.0f;
        }
        int tint = (alpha & 0xFF) << 24 | 0x00FFFFFF;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, u, v,
                screen.width, screen.height, BACKGROUND_WIDTH, BACKGROUND_HEIGHT,
                sourceWidth, sourceHeight, tint);
    }

    public static int glassPanelColor() {
        ModConfig config = ModConfig.get();
        return withAlpha(ThemeColors.background(config.uiBackgroundColor, 0x98),
                Math.round(0x98 * ModConfig.UI_PANEL_OPACITY));
    }

    public static int textColor() {
        return ModConfig.parseColor(ModConfig.get().uiTextColor, 0xFFF1F5FC);
    }

    public static int mutedTextColor() {
        return ModConfig.parseColor(ModConfig.get().uiMutedColor, 0xFF9BA9BB);
    }

    public static void drawGlassPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        int accent = ThemeColors.accent(ModConfig.get().uiAccentColor);
        fillRounded(graphics, x, y, width, height, 7, glassPanelColor());
        SmoothHudRenderer.drawRoundedRect(graphics, x + 8, y + 1, width - 16, 2, 1,
                withAlpha(0xFFFFFFFF, 0x24));
        SmoothHudRenderer.drawRoundedRect(graphics, x + 8, y + 3, width - 16, 2, 1,
                withAlpha(accent, 0x48));
    }

    private static float hoverAmount(AbstractButton button, boolean hovered) {
        long now = System.nanoTime();
        HoverState state = BUTTON_HOVERS.computeIfAbsent(button, ignored -> new HoverState());
        float elapsed = Math.min(0.1f, (now - state.lastUpdate) / 1_000_000_000.0f);
        state.lastUpdate = now;
        float target = hovered ? 1.0f : 0.0f;
        float step = elapsed / 0.14f;
        state.amount = target > state.amount
                ? Math.min(target, state.amount + step)
                : Math.max(target, state.amount - step);
        return state.amount;
    }

    private static void fillRounded(GuiGraphics graphics, int x, int y, int width, int height,
                                    int radius, int color) {
        SmoothHudRenderer.drawRoundedRect(graphics, x, y, width, height, radius, color);
    }

    private static final class HoverState {
        private float amount;
        private long lastUpdate = System.nanoTime();
    }

    public static void drawTitleLogo(GuiGraphics graphics, int screenWidth, float alpha) {
        int screenHeight = Minecraft.getInstance().screen == null
                ? 480 : Minecraft.getInstance().screen.height;
        int titleY = Math.max(24, screenHeight / 12);
        int white = Math.round(255 * alpha) << 24 | 0x00FFFFFF;
        int muted = Math.round(190 * alpha) << 24 | 0x00D6E0ED;
        SmoothHudRenderer.drawCenteredText(graphics, "wvDoorClient",
                screenWidth / 2 - 150, titleY, 300, 24, white);
        SmoothHudRenderer.drawRoundedRect(graphics, screenWidth / 2 - 42, titleY + 27, 84, 2, 1,
                withAlpha(ThemeColors.accent(ModConfig.get().uiAccentColor), Math.round(0xD0 * alpha)));
        SmoothHudRenderer.drawCenteredText(graphics, UiText.tr("VISUAL CLIENT"),
                screenWidth / 2 - 130, titleY + 33, 260, 18, muted);
    }

    private static int mix(int from, int to, float amount) {
        float clamped = Math.max(0.0f, Math.min(1.0f, amount));
        int alpha = interpolate(from >>> 24, to >>> 24, clamped);
        int red = interpolate((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, clamped);
        int green = interpolate((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, clamped);
        int blue = interpolate(from & 0xFF, to & 0xFF, clamped);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int interpolate(int from, int to, float amount) {
        return Math.round(from + (to - from) * amount);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha & 0xFF) << 24;
    }
}
