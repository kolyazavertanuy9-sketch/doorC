package dev.crystalutility.mixin;

import dev.crystalutility.FeatureState;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "setup", at = @At("TAIL"))
    private void crystalutility$applyFreeLook(Level level, Entity entity, boolean detached,
                                               boolean mirrored, float partialTick, CallbackInfo ci) {
        if (FeatureState.freeLook()) {
            ((CameraAccessor) this).crystalutility$setRotation(FeatureState.freeLookYaw(), FeatureState.freeLookPitch());
        }
    }
}
