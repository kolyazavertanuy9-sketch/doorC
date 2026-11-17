package dev.crystalutility.mixin;

import dev.crystalutility.config.ModConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void crystalutility$filterParticles(ParticleOptions options, double x, double y, double z,
                                                double velocityX, double velocityY, double velocityZ, CallbackInfo ci) {
        if (shouldRemove(options) || shouldReduce(options, false)) ci.cancel();
    }

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void crystalutility$filterForcedParticles(ParticleOptions options, boolean force, boolean important,
                                                      double x, double y, double z,
                                                      double velocityX, double velocityY, double velocityZ, CallbackInfo ci) {
        if (shouldRemove(options) || shouldReduce(options, important)) ci.cancel();
    }

    private static boolean shouldRemove(ParticleOptions options) {
        if (!ModConfig.get().crystalOptimizationEnabled || !ModConfig.get().removeCrystalParticles) return false;
        return options.getType() == ParticleTypes.EXPLOSION
                || options.getType() == ParticleTypes.EXPLOSION_EMITTER
                || options.getType() == ParticleTypes.SMOKE
                || options.getType() == ParticleTypes.LARGE_SMOKE;
    }

    private static boolean shouldReduce(ParticleOptions options, boolean important) {
        ModConfig config = ModConfig.get();
        if (!config.particleChangerEnabled || important || config.particleMode == 0) return false;
        float chance = config.particleMode == 1 ? 0.45f : 0.85f;
        return ThreadLocalRandom.current().nextFloat() < chance;
    }
}
