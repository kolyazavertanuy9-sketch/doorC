package dev.crystalutility.mixin;

import dev.crystalutility.render.ThemedUiRenderer;
import dev.crystalutility.render.SmoothHudRenderer;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class ThemedButtonMixin {
    @Inject(method = "renderDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void crystalutility$drawThemedButton(GuiGraphics graphics, CallbackInfo ci) {
        ThemedUiRenderer.drawButton(graphics, (AbstractButton) (Object) this);
        ci.cancel();
    }

    @Inject(method = "renderDefaultLabel", at = @At("HEAD"), cancellable = true)
    private void crystalutility$replacePixelatedButtonLabel(
            ActiveTextCollector collector, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void crystalutility$drawSmoothButtonLabel(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        AbstractButton button = (AbstractButton) (Object) this;
        if (!button.visible) return;
        int color = button.active ? 0xFFFFFFFF : 0xFF9A9A9A;
        float alpha = button.getAlpha();
        int argb = (Math.round(((color >>> 24) & 0xFF) * alpha) << 24) | (color & 0x00FFFFFF);
        int textWidth = Math.max(0, button.getWidth() - 8);
        String label = SmoothHudRenderer.fitText(button.getMessage().getString(), textWidth);
        SmoothHudRenderer.drawCenteredText(graphics, label,
                button.getX() + 4, button.getY(), textWidth,
                button.getHeight(), argb);
    }
}
