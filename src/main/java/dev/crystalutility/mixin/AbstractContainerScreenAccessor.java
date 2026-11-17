package dev.crystalutility.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int crystalutility$getLeftPos();

    @Accessor("topPos")
    int crystalutility$getTopPos();

    @Accessor("imageWidth")
    int crystalutility$getImageWidth();

    @Accessor("imageHeight")
    int crystalutility$getImageHeight();
}
