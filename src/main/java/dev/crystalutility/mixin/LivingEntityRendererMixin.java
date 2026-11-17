package dev.crystalutility.mixin;

import dev.crystalutility.render.ShieldCooldownRenderState;
import dev.crystalutility.render.ShieldCooldownTracker;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void crystalutility$captureShieldCooldown(
            LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        if (!(state instanceof ArmedEntityRenderState armedState)) return;

        boolean shieldDisabled = false;
        if (entity instanceof Player player) {
            shieldDisabled = ShieldCooldownTracker.isDisabled(player);
        }
        ((ShieldCooldownRenderState) armedState).crystalutility$setShieldDisabled(shieldDisabled);
    }
}
