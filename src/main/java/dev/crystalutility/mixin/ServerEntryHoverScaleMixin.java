package dev.crystalutility.mixin;

import dev.crystalutility.render.UiAnimations;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$OnlineServerEntry")
public abstract class ServerEntryHoverScaleMixin {
    @Shadow
    @Final
    private ServerSelectionList field_19117;

    @Unique
    private boolean crystalutility$scalePushed;

    @Inject(method = "renderContent", at = @At("HEAD"))
    private void crystalutility$beginHoverScale(
            GuiGraphics graphics, int mouseX, int mouseY, boolean hovered,
            float partialTick, CallbackInfo ci) {
        float amount = UiAnimations.approach(this, hovered ? 1.0f : 0.0f,
                0.0f, 135_000_000L);
        if (amount <= 0.001f) return;

        int index = field_19117.children().indexOf(this);
        if (index < 0) return;
        int left = field_19117.getRowLeft();
        int top = field_19117.getRowTop(index);
        int width = field_19117.getRowWidth();
        int height = field_19117.getRowBottom(index) - top;
        float scale = 1.0f + amount * 0.045f;
        float centerX = left + width / 2.0f;
        float centerY = top + height / 2.0f;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-centerX, -centerY);
        crystalutility$scalePushed = true;
    }

    @Inject(method = "renderContent", at = @At("RETURN"))
    private void crystalutility$endHoverScale(
            GuiGraphics graphics, int mouseX, int mouseY, boolean hovered,
            float partialTick, CallbackInfo ci) {
        if (!crystalutility$scalePushed) return;
        graphics.pose().popMatrix();
        crystalutility$scalePushed = false;
    }
}
