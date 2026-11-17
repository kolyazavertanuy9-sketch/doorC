package dev.crystalutility.mixin;

import dev.crystalutility.render.LoadingScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ReloadInstance reload;

    @Shadow
    @Final
    private boolean fadeIn;

    @Shadow
    private float currentProgress;

    @Shadow
    private long fadeOutStart;

    @Shadow
    private long fadeInStart;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void crystalutility$renderCustomLoadingScreen(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        long now = Util.getMillis();
        if (fadeIn && fadeInStart == -1L) fadeInStart = now;

        float fadeOutProgress = fadeOutStart > 0L
                ? (now - fadeOutStart) / 1000.0f
                : -1.0f;
        if (fadeOutProgress >= 2.0f) {
            minecraft.setOverlay(null);
            ci.cancel();
            return;
        }

        float alpha = 1.0f;
        if (fadeIn && fadeInStart >= 0L) {
            alpha = Mth.clamp((now - fadeInStart) / 500.0f, 0.0f, 1.0f);
        }
        if (fadeOutProgress >= 1.0f) {
            alpha = 1.0f - Mth.clamp(fadeOutProgress - 1.0f, 0.0f, 1.0f);
        }

        currentProgress = Mth.clamp(
                currentProgress * 0.95f + reload.getActualProgress() * 0.05f,
                0.0f, 1.0f);
        LoadingScreenRenderer.render(graphics, currentProgress, alpha, now);
        ci.cancel();
    }
}
