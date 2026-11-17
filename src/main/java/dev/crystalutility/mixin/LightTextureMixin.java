package dev.crystalutility.mixin;

import dev.crystalutility.config.ModConfig;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @ModifyArg(
            method = "updateLightTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                    ordinal = 0),
            index = 0)
    private float crystalutility$fullbrightAmbientFactor(float ambientFactor) {
        return ModConfig.get().fullbrightEnabled ? 1.0f : ambientFactor;
    }

    @ModifyArg(
            method = "updateLightTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                    ordinal = 1),
            index = 0)
    private Vector3fc crystalutility$fullbrightAmbientColor(Vector3fc ambientColor) {
        return ModConfig.get().fullbrightEnabled ? new Vector3f(1.0f) : ambientColor;
    }

    @Inject(method = "calculateDarknessScale(Lnet/minecraft/world/entity/LivingEntity;FF)F",
            at = @At("HEAD"), cancellable = true)
    private void wvdoorclient$disableDarkness(LivingEntity entity, float partialTick, float effectScale,
                                              CallbackInfoReturnable<Float> cir) {
        if (ModConfig.get().antiDarknessEnabled) {
            cir.setReturnValue(0.0f);
        }
    }
}
