package dev.crystalutility.render;

import dev.crystalutility.config.ModConfig;
import dev.crystalutility.media.MediaController;
import dev.crystalutility.media.MediaController.Command;
import dev.crystalutility.media.MediaSnapshot;
import dev.crystalutility.text.UiText;
import dev.wvlib.api.ThemeColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class MediaHud {
    private static final int PANEL_WIDTH = 286;
    private static final int COLLAPSED_HEIGHT = 38;
    private static final int EXPANDED_HEIGHT = 60;
    private static final int[] CONTROL_X = {110, 136, 162};
    private static final Object[] CONTROL_ANIMATION_KEYS = {new Object(), new Object(), new Object()};
    private static final Object EXPANSION_ANIMATION_KEY = new Object();
    private static float expansion;
    private static int drawnLeft;
    private static int drawnTop;
    private static float drawnScale = 1.0f;

    private MediaHud() {}

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) return;
        render(graphics, minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
                minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()), true);
    }

    public static void renderOnScreen(GuiGraphics graphics, int mouseX, int mouseY) {
        render(graphics, mouseX, mouseY, true);
    }

    private static void render(GuiGraphics graphics, double mouseX, double mouseY, boolean canHover) {
        ModConfig config = ModConfig.get();
        if (!config.showMediaHud) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;

        MediaSnapshot track = MediaController.displaySnapshot();
        if (!track.hasTrack()) return;

        long age = MediaController.trackAgeMillis();
        float transition;
        if (age < 180 && MediaController.previousTrackHasTrack()) {
            transition = 1.0f - smoothstep(age / 180.0f);
        } else if (age < 460 && MediaController.previousTrackHasTrack()) {
            transition = smoothstep(Math.min(1.0f, Math.max(0, age - 180) / 280.0f));
        } else if (age < 280) {
            transition = smoothstep(age / 280.0f);
        } else {
            transition = 1.0f;
        }
        int alpha = Math.round(255 * transition);
        if (alpha < 2) return;

        int left = HudLayout.x(config, HudLayout.Widget.MEDIA, graphics.guiWidth(), graphics.guiHeight());
        int top = HudLayout.y(config, HudLayout.Widget.MEDIA, graphics.guiWidth(), graphics.guiHeight());
        int density = SmoothHudRenderer.mediaTextureDensity();
        float scale = HudLayout.scale(config, HudLayout.Widget.MEDIA);
        float animatedHeight = COLLAPSED_HEIGHT + (EXPANDED_HEIGHT - COLLAPSED_HEIGHT) * expansion;
        double localMouseX = (mouseX - left) / scale;
        double localMouseY = (mouseY - top) / scale;
        boolean hovered = canHover && localMouseX >= 0 && localMouseX <= PANEL_WIDTH
                && localMouseY >= 0 && localMouseY <= animatedHeight + 8;
        expansion = UiAnimations.approach(EXPANSION_ANIMATION_KEY,
                hovered ? 1.0f : 0.0f, 0.0f, 240_000_000L);
        expansion = smoothstep(expansion);
        drawnLeft = left;
        drawnTop = top;
        drawnScale = scale;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(left, top);
            graphics.pose().scale(scale, scale);
            graphics.pose().translate(-left, -top);
            renderTrack(graphics, minecraft, config, track, transition, alpha,
                    left, top, density, expansion, mouseX, mouseY);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static boolean handleClick(double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        ModConfig config = ModConfig.get();
        if (!config.showMediaHud || minecraft.player == null || minecraft.options.hideGui
                || !MediaController.snapshot().hasTrack() || expansion < 0.72f) {
            return false;
        }

        double localX = (mouseX - drawnLeft) / drawnScale;
        double localY = (mouseY - drawnTop) / drawnScale;
        int controlY = 28;
        if (localY < controlY - 2 || localY > controlY + 26) return false;

        for (int i = 0; i < CONTROL_X.length; i++) {
            if (localX >= CONTROL_X[i] && localX <= CONTROL_X[i] + 24) {
                MediaController.send(switch (i) {
                    case 0 -> Command.PREVIOUS;
                    case 1 -> Command.TOGGLE;
                    case 2 -> Command.NEXT;
                    default -> throw new IllegalStateException("Unexpected media control index: " + i);
                });
                return true;
            }
        }
        return false;
    }

    private static void renderTrack(
            GuiGraphics graphics, Minecraft minecraft, ModConfig config, MediaSnapshot track,
            float transition, int alpha, int left, int top, int density, float expanded,
            double mouseX, double mouseY) {
        int panelHeight = Math.round(COLLAPSED_HEIGHT + (EXPANDED_HEIGHT - COLLAPSED_HEIGHT) * expanded);
        int panel = ThemeColors.background(config.uiBackgroundColor, 0xD8);
        SmoothHudRenderer.drawMediaPanel(graphics, left, top, PANEL_WIDTH, panelHeight, panel, density);

        int textAlpha = Math.round(255 * transition);
        int primary = (textAlpha << 24) | 0xF4F6FA;
        int secondary = (Math.round(textAlpha * 0.68f) << 24) | 0xD3D9E2;
        int accent = (textAlpha << 24) | (ThemeColors.accent(config.uiAccentColor) & 0x00FFFFFF);
        float textShift = smoothstep(Math.min(1.0f, expanded / 0.75f));
        float contentReveal = smoothstep(Math.max(0.0f, (expanded - 0.45f) / 0.55f));
        int textOffset = 16 + Math.round(46 * textShift);

        if (contentReveal > 0.01f) {
            Identifier artwork = MediaArtwork.texture(track.artworkBase64(), density);
            int artworkAlpha = Math.round(textAlpha * contentReveal);
            if (artwork != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, artwork, left + 10, top + 10,
                        0, 0, 40, 40, 40 * density, 40 * density,
                        40 * density, 40 * density, artworkAlpha << 24 | 0x00FFFFFF);
            } else {
                SmoothHudRenderer.drawMediaTile(graphics, left + 10, top + 10, 40,
                        (Math.round(artworkAlpha * 0.72f) << 24)
                                | (ModConfig.parseColor(config.uiBackgroundColor, 0xFF090B12) & 0x00FFFFFF),
                        ThemeColors.accent(config.uiAccentColor), density);
            }
        }

        String title = truncate(
                track.title().isBlank() ? UiText.tr("Unknown title") : track.title(),
                PANEL_WIDTH - textOffset - 16);
        String artist = truncate(
                track.artist().isBlank() ? UiText.tr("Unknown artist") : track.artist(),
                PANEL_WIDTH - textOffset - 16);
        int textLeft = left + textOffset;
        SmoothHudRenderer.drawMediaText(graphics, title, textLeft, top + 6, primary, density);
        SmoothHudRenderer.drawMediaText(graphics, artist, textLeft, top + 18, secondary, density);

        float scale = drawnScale;
        double localMouseX = (mouseX - left) / scale;
        double localMouseY = (mouseY - top) / scale;
        if (contentReveal > 0.01f) {
            for (int i = 0; i < CONTROL_X.length; i++) {
                int controlX = CONTROL_X[i];
                boolean controlHovered = localMouseX >= controlX && localMouseX <= controlX + 24
                        && localMouseY >= 27 && localMouseY <= 52;
                float hover = UiAnimations.approach(CONTROL_ANIMATION_KEYS[i],
                        controlHovered ? 1.0f : 0.0f,
                        0.0f, 120_000_000L);
                float controlScale = 1.0f + hover * 0.14f;
                float centerX = left + controlX + 12;
                float centerY = top + 40;
                graphics.pose().pushMatrix();
                graphics.pose().translate(centerX, centerY);
                graphics.pose().scale(controlScale, controlScale);
                graphics.pose().translate(-centerX, -centerY);
                SmoothHudRenderer.drawMediaControl(graphics, left + controlX, top + 28,
                        i, track.playing(), controlHovered, ThemeColors.accent(config.uiAccentColor), density,
                        Math.round(textAlpha * contentReveal));
                graphics.pose().popMatrix();
            }
        }

        int progressLeft = left + textOffset;
        int progressRight = left + PANEL_WIDTH - Math.round(16 + 4 * expanded);
        int progressY = top + panelHeight - 4;
        SmoothHudRenderer.drawRoundedRect(graphics, progressLeft, progressY,
                progressRight - progressLeft, 2, 1, (Math.round(alpha * 0.35f) << 24) | 0xFFFFFF);
        if (track.durationMillis() > 0) {
            float progress = Math.max(0f, Math.min(1f,
                    (float) track.positionMillis() / track.durationMillis()));
            SmoothHudRenderer.drawRoundedRect(graphics, progressLeft, progressY,
                    Math.round((progressRight - progressLeft) * progress), 2, 1, accent);
        }
    }

    private static String truncate(String text, int maxWidth) {
        if (SmoothHudRenderer.mediaTextWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        while (!text.isEmpty() && SmoothHudRenderer.mediaTextWidth(text + ellipsis) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }

    private static float smoothstep(float value) {
        float clamped = Math.max(0.0f, Math.min(1.0f, value));
        return clamped * clamped * (3 - 2 * clamped);
    }
}
