package dev.crystalutility.mixin;

import dev.crystalutility.config.ModConfig;
import dev.wvlib.api.ThemeColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Inject(method = "renderBg", at = @At("TAIL"))
    private void crystalutility$themePlayerInventory(
            GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        ModConfig config = ModConfig.get();
        AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) (Object) this;
        int left = screen.crystalutility$getLeftPos();
        int top = screen.crystalutility$getTopPos();
        int right = left + screen.crystalutility$getImageWidth();
        int bottom = top + screen.crystalutility$getImageHeight();
        int accent = ThemeColors.accent(config.uiAccentColor);

        graphics.fill(left + 2, top + 2, right - 2, bottom - 2,
                ThemeColors.background(config.uiBackgroundColor, 0x36));
        graphics.fill(left + 2, top + 1, right - 2, top + 3, accent);
        graphics.fill(left + 1, top + 3, left + 3, bottom - 3, accent);
        graphics.fill(right - 3, top + 3, right - 1, bottom - 3,
                (accent & 0x00FFFFFF) | 0xAA000000);
        graphics.fill(left + 3, bottom - 3, right - 3, bottom - 1,
                (accent & 0x00FFFFFF) | 0xAA000000);
    }
}
