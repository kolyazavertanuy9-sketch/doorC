package dev.crystalutility.mixin;

import dev.crystalutility.CrystalVisibility;
import dev.crystalutility.config.ModConfig;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCrystalRenderer.class)
public abstract class EndCrystalRendererMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void crystalutility$skipHiddenCrystal(EndCrystalRenderState state, PoseStack poseStack,
                                                  SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (ModConfig.get().crystalOptimizationEnabled && CrystalVisibility.shouldHideAt(state.x, state.y, state.z)) {
            ci.cancel();
        }
    }
}
