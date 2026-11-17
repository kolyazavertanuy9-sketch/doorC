package dev.crystalutility.mixin;

import dev.crystalutility.render.ThemedUiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ThemedScreenBackgroundMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void crystalutility$drawAnimatedMenuBackground(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (Minecraft.getInstance().level == null) {
            ThemedUiRenderer.drawScreenBackdrop(graphics, (Screen) (Object) this, 0);
            ci.cancel();
        }
    }
}
