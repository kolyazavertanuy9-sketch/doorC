package dev.crystalutility.mixin;

import dev.crystalutility.render.UiAnimations;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractWidget.class)
public abstract class WidgetHoverScaleMixin {
    @Shadow
    public boolean visible;

    @Unique
    private boolean crystalutility$scalePushed;

    @Inject(method = "render", at = @At("HEAD"))
    private void crystalutility$beginHoverScale(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        AbstractWidget widget = (AbstractWidget) (Object) this;
        if (!visible || !(widget instanceof AbstractButton button)) return;

        float hover = UiAnimations.approach(widget, button.isActive() && button.isHoveredOrFocused()
                ? 1.0f : 0.0f, 0.0f, 125_000_000L);
        if (hover <= 0.001f) return;

        float scale = 1.0f + hover * 0.06f;
        float centerX = widget.getX() + widget.getWidth() / 2.0f;
        float centerY = widget.getY() + widget.getHeight() / 2.0f;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-centerX, -centerY);
        crystalutility$scalePushed = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void crystalutility$endHoverScale(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!crystalutility$scalePushed) return;
        graphics.pose().popMatrix();
        crystalutility$scalePushed = false;
    }
}
