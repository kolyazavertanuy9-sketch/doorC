package dev.crystalutility.mixin;

import dev.crystalutility.config.ModConfig;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void crystalutility$toggleBlockOutline(MultiBufferSource.BufferSource bufferSource,
                                                    PoseStack poseStack, boolean translucent,
                                                    LevelRenderState renderState, CallbackInfo ci) {
        ModConfig config = ModConfig.get();
        if (!config.blockOutlineEnabled || config.blockHighlightEnabled) {
            ci.cancel();
        }
    }
}
