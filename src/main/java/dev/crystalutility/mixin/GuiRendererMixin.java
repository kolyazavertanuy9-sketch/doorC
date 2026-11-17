package dev.crystalutility.mixin;

import dev.crystalutility.render.ShieldCooldownRenderState;
import dev.crystalutility.render.ShieldRenderContext;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Redirect(
            method = "prepareItemElements",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;forEachItem(Ljava/util/function/Consumer;)V"))
    private void crystalutility$renderItemsWithShieldState(
            GuiRenderState renderState,
            Consumer<GuiItemRenderState> itemConsumer) {
        renderState.forEachItem(itemRenderState -> {
            boolean shieldDisabled = ((ShieldCooldownRenderState) (Object) itemRenderState)
                    .crystalutility$isShieldDisabled();
            ShieldRenderContext.withShieldDisabled(shieldDisabled, () -> itemConsumer.accept(itemRenderState));
        });
    }
}
