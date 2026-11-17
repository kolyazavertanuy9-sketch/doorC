package dev.crystalutility.mixin;

import dev.crystalutility.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftUseDelayMixin {
    @Shadow public LocalPlayer player;
    @Shadow public ClientLevel level;
    @Shadow public HitResult hitResult;
    @Shadow private int rightClickDelay;

    @Inject(method = "startUseItem", at = @At("TAIL"))
    private void crystalutility$reduceCrystalAndAnchorUseDelay(CallbackInfo ci) {
        if (player == null || level == null || !(hitResult instanceof BlockHitResult blockHit)) return;

        ModConfig config = ModConfig.get();
        ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
        boolean crystalPlacement = config.crystalOptimizationEnabled
                && (mainHand.is(Items.END_CRYSTAL) || offHand.is(Items.END_CRYSTAL))
                && (level.getBlockState(blockHit.getBlockPos()).is(Blocks.OBSIDIAN)
                || level.getBlockState(blockHit.getBlockPos()).is(Blocks.BEDROCK));

        BlockPos position = blockHit.getBlockPos();
        boolean anchorInteraction = config.anchorOptimizationEnabled
                && level.getBlockState(position).is(Blocks.RESPAWN_ANCHOR);
        if (crystalPlacement || anchorInteraction) {
            rightClickDelay = 0;
        }
    }
}
