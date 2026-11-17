package dev.crystalutility.mixin;

import dev.crystalutility.config.ModConfig;
import dev.crystalutility.skin.ClientSkinManager;
import dev.crystalutility.skin.SkinChangerScreen;
import dev.crystalutility.text.UiText;
import dev.wvlib.api.ThemeColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenSkinMixin {
    private static final int SKIN_PANEL_WIDTH = 118;
    private static final int SKIN_PANEL_HEIGHT = 164;

    @Inject(method = "init", at = @At("TAIL"))
    private void crystalutility$addSkinMenuButton(CallbackInfo ci) {
        PauseScreen screen = (PauseScreen) (Object) this;
        int panelX = skinPanelX(screen.width);
        int panelY = skinPanelY(screen.height);
        ((ScreenAccessor) screen).crystalutility$addRenderableWidget(
                Button.builder(Component.literal(UiText.tr("Skin Menu")),
                                button -> Minecraft.getInstance().setScreen(new SkinChangerScreen(screen)))
                        .bounds(panelX + 7, panelY + SKIN_PANEL_HEIGHT - 27,
                                SKIN_PANEL_WIDTH - 14, 20)
                        .build());
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void crystalutility$renderSkinPreview(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        PauseScreen screen = (PauseScreen) (Object) this;
        int panelX = skinPanelX(screen.width);
        int panelY = skinPanelY(screen.height);
        int accent = ThemeColors.accent(ModConfig.get().uiAccentColor);
        int buttonY = panelY + SKIN_PANEL_HEIGHT - 27;
        int cardBottom = buttonY - 3;
        graphics.fill(panelX + 2, panelY, panelX + SKIN_PANEL_WIDTH - 2,
                cardBottom, 0xA810141D);
        graphics.fill(panelX, panelY + 2, panelX + SKIN_PANEL_WIDTH,
                cardBottom - 2, 0xA810141D);
        graphics.fill(panelX + 8, panelY + 8, panelX + SKIN_PANEL_WIDTH - 8,
                panelY + 9, accent);
        graphics.drawCenteredString(client.font, UiText.tr("YOUR SKIN"),
                panelX + SKIN_PANEL_WIDTH / 2, panelY + 15, 0xFFE8EDF5);

        ClientSkinManager.drawAccountAvatar(client, graphics,
                panelX + 10, panelY + 30, panelX + SKIN_PANEL_WIDTH - 10, cardBottom - 5,
                52, mouseX, mouseY, partialTick);

    }

    private static int skinPanelX(int screenWidth) {
        return Math.max(8, Math.min(screenWidth - SKIN_PANEL_WIDTH - 8,
                screenWidth / 2 - 100 - SKIN_PANEL_WIDTH - 12));
    }

    private static int skinPanelY(int screenHeight) {
        return Math.max(8, screenHeight / 2 - SKIN_PANEL_HEIGHT / 2 - 70);
    }
}
