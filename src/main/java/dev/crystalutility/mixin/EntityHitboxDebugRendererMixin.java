package dev.crystalutility.mixin;

import dev.crystalutility.config.ModConfig;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {
    @Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true)
    private void wvdoorclient$replaceVanillaHitboxes(CallbackInfo ci) {
        if (ModConfig.get().hitboxesEnabled) {
            ci.cancel();
        }
    }
}
