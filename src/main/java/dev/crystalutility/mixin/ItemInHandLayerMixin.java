package dev.crystalutility.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.crystalutility.render.ShieldCooldownRenderState;
import dev.crystalutility.render.ShieldRenderContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Redirect(
            method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void crystalutility$submitWithShieldOwnerState(
            ItemStackRenderState itemRenderState,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            int overlay,
            int outlineColor,
            @Local(argsOnly = true) ArmedEntityRenderState entityState,
            @Local(argsOnly = true) ItemStack itemStack) {
        ShieldRenderContext.submit(itemRenderState, poseStack, collector, light, overlay, outlineColor,
                itemStack.is(Items.SHIELD)
                        && ((ShieldCooldownRenderState) entityState).crystalutility$isShieldDisabled());
    }
}
