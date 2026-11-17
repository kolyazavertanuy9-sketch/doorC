package dev.crystalutility.labels;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import dev.crystalutility.config.ModConfig;
import dev.crystalutility.render.SmoothHudRenderer;
import dev.crystalutility.render.ThemedUiRenderer;
import dev.crystalutility.text.UiText;
import dev.wvlib.api.ThemeColors;

public final class BlockLabelEditorScreen extends Screen {
    private final Screen parent;
    private final BlockPos targetPos;
    private EditBox input;

    private BlockLabelEditorScreen(Screen parent, BlockPos targetPos) {
        super(Component.literal(UiText.tr("Block label")));
        this.parent = parent;
        this.targetPos = targetPos;
    }

    public static Screen create(Screen parent, BlockPos targetPos) {
        return new BlockLabelEditorScreen(parent, targetPos);
    }

    @Override
    protected void init() {
        int panelWidth = 360;
        int panelHeight = 140;
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;

        input = new EditBox(font, left + 18, top + 42, panelWidth - 36, 20,
                Component.literal(UiText.tr("Label text")));
        input.setMaxLength(32);
        input.setHint(Component.literal(UiText.tr("Name this block")));
        input.setValue(UiText.tr("Marker"));
        addRenderableWidget(input);
        addRenderableWidget(Button.builder(Component.literal(UiText.tr("Save")), button -> saveLabel())
                .bounds(left + panelWidth - 172, top + panelHeight - 38, 72, 22)
                .build());
        addRenderableWidget(Button.builder(Component.literal(UiText.tr("Cancel")), button -> onClose())
                .bounds(left + panelWidth - 92, top + panelHeight - 38, 72, 22)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ThemedUiRenderer.drawScreenBackdrop(graphics, this, 0xCC0B0F16);
        int panelWidth = 360;
        int panelHeight = 140;
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;

        ThemedUiRenderer.drawGlassPanel(graphics, left, top, panelWidth, panelHeight);
        SmoothHudRenderer.drawRoundedRect(graphics, left + 14, top + 1,
                panelWidth - 28, 3, 1, ThemeColors.accent(ModConfig.get().uiAccentColor));
        graphics.drawString(font, UiText.tr("Create block label"), left + 18, top + 16, 0xFFEFF5FF, false);
        graphics.drawString(font, UiText.tr("The mark will be saved for this block position."), left + 18, top + 30,
                0xFF9BA9BB, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            saveLabel();
            return true;
        }
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void saveLabel() {
        String text = input == null ? UiText.tr("Marker") : input.getValue().trim();
        if (text.isEmpty()) text = UiText.tr("Marker");
        BlockLabelManager.addOrUpdateAt(targetPos, text);
        onClose();
    }
}
