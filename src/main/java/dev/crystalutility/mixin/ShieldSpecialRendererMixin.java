package dev.crystalutility.mixin;

import dev.crystalutility.config.ModConfig;
import dev.crystalutility.render.ShieldRenderContext;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ShieldSpecialRenderer.class)
public abstract class ShieldSpecialRendererMixin {
    @ModifyArg(
            method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ZZILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;I)V",
                    ordinal = 1),
            index = 8)
    private int crystalutility$tintShieldItem(
            int originalColor, @Local(argsOnly = true) DataComponentMap shieldComponents) {
        ModConfig config = ModConfig.get();
        if (!config.shieldModelColorEnabled || shieldComponents == null) return originalColor;

        boolean shieldDisabled = ShieldRenderContext.isShieldDisabled();
        return ModConfig.parseColor(
                shieldDisabled ? config.shieldModelBrokenColor : config.shieldModelReadyColor,
                originalColor);
    }
}
