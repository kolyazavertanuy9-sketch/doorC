package dev.crystalutility.mixin;

import dev.crystalutility.render.ThemedUiRenderer;
import dev.crystalutility.render.SmoothHudRenderer;
import dev.crystalutility.CrystalUtilityClient;
import dev.crystalutility.config.ModConfig;
import dev.crystalutility.skin.ClientSkinManager;
import dev.crystalutility.skin.SkinChangerScreen;
import dev.crystalutility.text.UiText;
import dev.wvlib.api.ThemeColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void crystalutility$addSettingsButton(CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        ((ScreenAccessor) screen).crystalutility$addRenderableWidget(Button.builder(Component.literal(UiText.tr("Client Settings")),
                        button -> Minecraft.getInstance().setScreen(CrystalUtilityClient.createConfigScreen(screen)))
                .bounds(8, screen.height - 28, 120, 20)
                .build());
        int panelY = skinPanelY(screen.height);
        int panelX = skinPanelX(screen.width);
        ((ScreenAccessor) screen).crystalutility$addRenderableWidget(Button.builder(Component.literal(UiText.tr("Skin Menu")),
                        button -> Minecraft.getInstance().setScreen(new SkinChangerScreen(screen)))
                .bounds(panelX + 7, panelY + SKIN_PANEL_HEIGHT - 27, SKIN_PANEL_WIDTH - 14, 20)
                .build());
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/TitleScreen;renderPanorama(Lnet/minecraft/client/gui/GuiGraphics;F)V",
                    shift = At.Shift.AFTER))
    private void crystalutility$drawAnimatedBackground(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ThemedUiRenderer.drawTitleBackground(graphics, (TitleScreen) (Object) this);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void crystalutility$renderAccountSkin(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        int panelY = skinPanelY(screen.height);
        int panelX = skinPanelX(screen.width);
        int accent = ThemeColors.accent(ModConfig.get().uiAccentColor);
        int buttonY = panelY + SKIN_PANEL_HEIGHT - 27;
        int cardBottom = buttonY - 3;
        ThemedUiRenderer.drawGlassPanel(graphics, panelX, panelY, SKIN_PANEL_WIDTH, cardBottom - panelY);
        graphics.fill(panelX + 10, panelY + 9, panelX + SKIN_PANEL_WIDTH - 10, panelY + 10, accent);
        SmoothHudRenderer.drawCenteredText(graphics, UiText.tr("YOUR SKIN"),
                panelX + 8, panelY + 12, SKIN_PANEL_WIDTH - 16, 16, 0xFFE8EDF5);
        ClientSkinManager.drawAccountAvatar(Minecraft.getInstance(), graphics,
                panelX + 10, panelY + 30, panelX + SKIN_PANEL_WIDTH - 10, cardBottom - 5,
                52, mouseX, mouseY, partialTick);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
    private void crystalutility$replaceVanillaLogo(
            LogoRenderer logoRenderer, GuiGraphics graphics, int screenWidth, float alpha) {
        ThemedUiRenderer.drawTitleLogo(graphics, screenWidth, alpha);
    }

    private static final int SKIN_PANEL_WIDTH = 118;
    private static final int SKIN_PANEL_HEIGHT = 164;

    private static int skinPanelY(int screenHeight) {
        return Math.max(8, Math.min(screenHeight - SKIN_PANEL_HEIGHT - 38,
                screenHeight / 2 - SKIN_PANEL_HEIGHT / 2));
    }

    private static int skinPanelX(int screenWidth) {
        return Math.max(8, Math.min(screenWidth - SKIN_PANEL_WIDTH - 8,
                screenWidth - SKIN_PANEL_WIDTH - 16));
    }
}
