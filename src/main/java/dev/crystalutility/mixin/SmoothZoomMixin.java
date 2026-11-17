package dev.crystalutility.mixin;

import dev.crystalutility.CrystalUtilityClient;
import dev.crystalutility.render.SmoothZoom;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class SmoothZoomMixin {
    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)F", at = @At("RETURN"), cancellable = true)
    private void crystalutility$applySmoothZoom(
            Camera camera, float partialTick, boolean changingFov,
            CallbackInfoReturnable<Float> cir) {
        Minecraft client = Minecraft.getInstance();
        boolean active = client.player != null && client.level != null && client.screen == null
                && CrystalUtilityClient.isSmoothZoomHeld();
        float zoom = SmoothZoom.update(active);
        cir.setReturnValue(cir.getReturnValue() / zoom);
    }
}
