package dev.crystalutility.mixin;

import dev.crystalutility.skin.ClientSkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerSkinMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void crystalutility$overrideLocalPlayerSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        if (Minecraft.getInstance().player == (Object) this) {
            cir.setReturnValue(ClientSkinManager.override(cir.getReturnValue()));
        }
    }
}
