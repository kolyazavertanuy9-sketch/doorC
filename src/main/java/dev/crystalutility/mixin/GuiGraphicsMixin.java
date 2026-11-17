package dev.crystalutility.mixin;

import dev.crystalutility.render.ShieldCooldownRenderState;
import dev.crystalutility.render.ShieldCooldownTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Redirect(
            method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;submitItem(Lnet/minecraft/client/gui/render/state/GuiItemRenderState;)V"))
    private void crystalutility$captureInventoryShieldState(
            GuiRenderState guiRenderState,
            GuiItemRenderState itemRenderState,
            LivingEntity entity,
            net.minecraft.world.level.Level level,
            ItemStack itemStack,
            int x,
            int y,
            int seed) {
        boolean shieldDisabled = itemStack.is(Items.SHIELD)
                && entity instanceof Player player
                && ShieldCooldownTracker.isDisabled(player);
        ((ShieldCooldownRenderState) (Object) itemRenderState).crystalutility$setShieldDisabled(shieldDisabled);
        guiRenderState.submitItem(itemRenderState);
    }
}
