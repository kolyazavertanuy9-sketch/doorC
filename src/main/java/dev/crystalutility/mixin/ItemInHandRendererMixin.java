package dev.crystalutility.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.crystalutility.render.ShieldRenderContext;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Redirect(
            method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void crystalutility$submitFirstPersonShieldWithOwnerState(
            ItemStackRenderState itemRenderState,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            int overlay,
            int outlineColor,
            @Local(argsOnly = true) LivingEntity entity,
            @Local(argsOnly = true) ItemStack itemStack) {
        boolean shieldDisabled = itemStack.is(Items.SHIELD)
                && entity instanceof Player player
                && player.getCooldowns().isOnCooldown(itemStack);
        ShieldRenderContext.submit(itemRenderState, poseStack, collector, light, overlay, outlineColor,
                shieldDisabled);
    }
}
