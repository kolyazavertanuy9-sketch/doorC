package dev.crystalutility.mixin;

import dev.crystalutility.config.ModConfig;
import dev.crystalutility.render.SmoothHudRenderer;
import dev.wvlib.api.ThemeColors;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void crystalutility$replaceVanillaCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ModConfig.get().customCrosshairEnabled) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "renderItemHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
                    ordinal = 0))
    private void crystalutility$renderThemedHotbar(
            GuiGraphics graphics, RenderPipeline pipeline, Identifier sprite,
            int x, int y, int width, int height) {
        if (!"hud/hotbar".equals(sprite.getPath())) {
            graphics.blitSprite(pipeline, sprite, x, y, width, height);
            return;
        }

        ModConfig config = ModConfig.get();
        int accent = ThemeColors.accent(config.uiAccentColor);
        int background = withAlpha(ThemeColors.background(config.uiBackgroundColor, 0xD8),
                Math.round(0xD8 * ModConfig.UI_PANEL_OPACITY));
        SmoothHudRenderer.drawRoundedRect(graphics, x, y, width, height, 7,
                withAlpha(accent, 0x70));
        SmoothHudRenderer.drawRoundedRect(graphics, x + 1, y + 1, width - 2, height - 2, 6, background);

        for (int slot = 0; slot < 9; slot++) {
            int slotX = x + 2 + slot * 20;
            SmoothHudRenderer.drawRoundedRect(graphics, slotX, y + 2, 18, 18, 4,
                    withAlpha(accent, 0x24));
            SmoothHudRenderer.drawRoundedRect(graphics, slotX + 1, y + 3, 16, 16, 3,
                    withAlpha(ThemeColors.background(config.uiBackgroundColor, 0xA0),
                            Math.round(0xA0 * ModConfig.UI_PANEL_OPACITY)));
        }
    }

    @Redirect(
            method = "renderItemHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
                    ordinal = 1))
    private void crystalutility$renderThemedSelectedSlot(
            GuiGraphics graphics, RenderPipeline pipeline, Identifier sprite,
            int x, int y, int width, int height) {
        if (!"hud/hotbar_selection".equals(sprite.getPath())) {
            graphics.blitSprite(pipeline, sprite, x, y, width, height);
            return;
        }

        ModConfig config = ModConfig.get();
        int accent = ThemeColors.accent(config.uiAccentColor);
        SmoothHudRenderer.drawRoundedRect(graphics, x, y, width, height, 7,
                withAlpha(accent, 0xD8));
        SmoothHudRenderer.drawRoundedRect(graphics, x + 1, y + 1, width - 2, height - 2, 6,
                withAlpha(ThemeColors.background(config.uiBackgroundColor, 0xD0), 0xD0));
        SmoothHudRenderer.drawRoundedRect(graphics, x + 5, y + height - 4,
                width - 10, 2, 1, accent);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha & 0xFF) << 24;
    }
}
