package dev.crystalutility.mixin;

import dev.crystalutility.FeatureState;
import dev.crystalutility.config.ModConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Shadow @Final private String name;
    @Shadow private boolean isDown;

    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void crystalutility$applyToggleMovement(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null) return;
        ModConfig config = ModConfig.get();
        if (config.toggleSprintEnabled && this.name.equals(client.options.keySprint.getName())
                && FeatureState.sprintToggled()) {
            cir.setReturnValue(true);
        } else if (config.toggleSneakEnabled && this.name.equals(client.options.keyShift.getName())
                && FeatureState.sneakToggled()) {
            cir.setReturnValue(true);
        }
    }
}
