package dev.crystalutility.mixin;

import dev.crystalutility.config.ModConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMotionBlurMixin {
    private static final String EFFECT_PREFIX = "crystalutility:motion_blur_";

    @Invoker("setPostEffect")
    protected abstract void crystalutility$setPostEffect(Identifier effectId);

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void crystalutility$updateMotionBlur(DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        Identifier activeEffect = ((GameRenderer) (Object) this).currentPostEffect();
        boolean isOurEffect = isMotionBlur(activeEffect);

        if (client.player == null || !ModConfig.get().motionBlurEnabled) {
            if (isOurEffect) {
                ((GameRenderer) (Object) this).clearPostEffect();
            }
            return;
        }

        Identifier desiredEffect = Identifier.parse(EFFECT_PREFIX
                + Math.max(1, Math.min(5, Math.round(ModConfig.get().motionBlurStrength * 10.0f))));
        if (activeEffect == null || isOurEffect && !desiredEffect.equals(activeEffect)) {
            crystalutility$setPostEffect(desiredEffect);
        }
    }

    private static boolean isMotionBlur(Identifier effectId) {
        return effectId != null && effectId.toString().startsWith(EFFECT_PREFIX);
    }
}
