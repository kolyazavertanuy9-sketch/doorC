package dev.crystalutility.render;

import dev.crystalutility.config.ModConfig;
import dev.wvlib.api.ThemeColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class VisualEffectsHud {
    private static final long CHUNK_FADE_NANOS = 350_000_000L;
    private static long chunkLoadedAt;

    private VisualEffectsHud() {}

    public static void onChunkLoad() {
        chunkLoadedAt = System.nanoTime();
    }

    public static void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        ModConfig config = ModConfig.get();
        if (client.player == null || client.options.hideGui || client.screen != null) {
            return;
        }

        if (config.chunkAnimatorEnabled && chunkLoadedAt != 0) {
            long elapsed = System.nanoTime() - chunkLoadedAt;
            if (elapsed < CHUNK_FADE_NANOS) {
                float progress = (float) elapsed / CHUNK_FADE_NANOS;
                int alpha = Math.round(22 * (1.0f - progress));
                int tint = (alpha << 24) | (ThemeColors.accent(config.uiAccentColor) & 0x00FFFFFF);
                drawEdgeFade(graphics, tint, 3);
            } else {
                chunkLoadedAt = 0;
            }
        }
    }

    private static void drawEdgeFade(GuiGraphics graphics, int color, int layers) {
        for (int i = 0; i < layers; i++) {
            int thickness = (i + 1) * 2;
            graphics.fill(0, 0, graphics.guiWidth(), thickness, color);
            graphics.fill(0, graphics.guiHeight() - thickness, graphics.guiWidth(),
                    graphics.guiHeight(), color);
            graphics.fill(0, 0, thickness, graphics.guiHeight(), color);
            graphics.fill(graphics.guiWidth() - thickness, 0, graphics.guiWidth(),
                    graphics.guiHeight(), color);
        }
    }

}
