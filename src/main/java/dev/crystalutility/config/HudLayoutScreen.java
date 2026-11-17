package dev.crystalutility.config;

import dev.crystalutility.render.HudLayout;
import dev.crystalutility.render.SmoothHudRenderer;
import dev.crystalutility.render.ThemedUiRenderer;
import dev.crystalutility.text.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class HudLayoutScreen extends Screen {
    private static final int MUTED = 0xFFBCC7D8;
    private final Screen parent;
    private final ModConfig config = ModConfig.get();
    private HudLayout.Widget dragging;
    private HudLayout.Widget resizing;
    private int dragOffsetX;
    private int dragOffsetY;
    private int resizeStartX;
    private int resizeStartY;
    private float resizeStartScale;

    public HudLayoutScreen(Screen parent) {
        super(Component.literal(UiText.tr("wvDoorClient HUD Layout")));
        this.parent = parent;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ThemedUiRenderer.drawScreenBackdrop(graphics, this, 0xA9080C13);
        graphics.drawCenteredString(font, UiText.tr("HUD LAYOUT"), width / 2, 16, 0xFFFFFFFF);
        graphics.drawCenteredString(font, UiText.tr("Drag blocks to move; drag the lower-right corner to resize (50%-200%)."),
                width / 2, 31, MUTED);

        for (HudLayout.Widget widget : HudLayout.Widget.values()) {
            drawWidget(graphics, widget, mouseX, mouseY);
        }

        int buttonWidth = 90;
        int buttonX = width - buttonWidth - 14;
        int buttonY = height - 39;
        panel(graphics, buttonX, buttonY, buttonWidth, 25, accentColor());
        graphics.drawCenteredString(font, UiText.tr("Done"), buttonX + buttonWidth / 2, buttonY + 8, 0xFFFFFFFF);
        graphics.drawString(font, UiText.tr("Right Shift • Finish editing"), 14, height - 27, MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && event.x() >= width - 104 && event.y() >= height - 44) {
            onClose();
            return true;
        }
        if (event.button() == 0) {
            for (HudLayout.Widget widget : HudLayout.Widget.values()) {
                int x = x(widget);
                int y = y(widget);
                int widgetWidth = HudLayout.width(config, widget);
                int widgetHeight = HudLayout.height(config, widget);
                if (inside(event.x(), event.y(), x + widgetWidth - 10, y + widgetHeight - 10, 10, 10)) {
                    resizing = widget;
                    resizeStartX = (int) event.x();
                    resizeStartY = (int) event.y();
                    resizeStartScale = HudLayout.scale(config, widget);
                    return true;
                }
                if (inside(event.x(), event.y(), x, y,
                        widgetWidth, widgetHeight)) {
                    dragging = widget;
                    dragOffsetX = (int) event.x() - x;
                    dragOffsetY = (int) event.y() - y;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (resizing != null && event.button() == 0) {
            HudLayout.Widget widget = resizing;
            float horizontalChange = (float) (event.x() - resizeStartX) / HudLayout.width(widget);
            float verticalChange = (float) (event.y() - resizeStartY) / HudLayout.baseHeight(config, widget);
            HudLayout.setScale(config, widget, resizeStartScale + (horizontalChange + verticalChange) / 2.0f);
            int x = x(widget);
            int y = y(widget);
            HudLayout.set(config, widget,
                    clamp(x, 0, width - HudLayout.width(config, widget)),
                    clamp(y, 0, height - HudLayout.height(config, widget)));
            return true;
        }
        if (dragging != null && event.button() == 0) {
            int nextX = clamp((int) event.x() - dragOffsetX, 0,
                    width - HudLayout.width(config, dragging));
            int nextY = clamp((int) event.y() - dragOffsetY, 0,
                    height - HudLayout.height(config, dragging));
            HudLayout.set(config, dragging, nextX, nextY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (resizing != null && event.button() == 0) {
            resizing = null;
            ModConfig.save();
            return true;
        }
        if (dragging != null && event.button() == 0) {
            dragging = null;
            ModConfig.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        ModConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }

    private void drawWidget(GuiGraphics graphics, HudLayout.Widget widget, int mouseX, int mouseY) {
        int x = x(widget);
        int y = y(widget);
        int widgetWidth = HudLayout.width(widget);
        int widgetHeight = HudLayout.baseHeight(config, widget);
        boolean hovered = inside(mouseX, mouseY, x, y,
                HudLayout.width(config, widget), HudLayout.height(config, widget));
        float scale = HudLayout.scale(config, widget);
        if (scale != 1.0f) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(scale, scale);
            graphics.pose().translate(-x, -y);
        }
        if (widget != HudLayout.Widget.KEYSTROKES && widget != HudLayout.Widget.CPS
                && widget != HudLayout.Widget.MEDIA) {
            panel(graphics, x, y, widgetWidth, widgetHeight,
                    hovered ? blend(backgroundColor(), 0xFFFFFFFF, 0.17f) : backgroundColor());
            SmoothHudRenderer.drawRoundedRect(graphics, x, y + 3, 2, widgetHeight - 6, 1, accentColor());
        }
        switch (widget) {
            case COORDINATES -> {
                text(graphics, "POSITION", x + 9, y + 6);
                text(graphics, "X 10165.9   Y 67.0   Z 100037.0", x + 9, y + 18);
                text(graphics, "Facing  NE", x + 9, y + 31, accentColor());
            }
            case KEYSTROKES -> {
                SmoothHudRenderer.drawKey(graphics, x + 28, y, 24, 24, "W", false, accentColor());
                SmoothHudRenderer.drawKey(graphics, x, y + 26, 24, 24, "A", false, accentColor());
                SmoothHudRenderer.drawKey(graphics, x + 28, y + 26, 24, 24, "S", false, accentColor());
                SmoothHudRenderer.drawKey(graphics, x + 56, y + 26, 24, 24, "D", false, accentColor());
                SmoothHudRenderer.drawKey(graphics, x, y + 52, 80, 18, "SPACE", false, accentColor());
                SmoothHudRenderer.drawKey(graphics, x, y + 73, 38, 18, "LMB", false, accentColor());
                SmoothHudRenderer.drawKey(graphics, x + 42, y + 73, 38, 18, "RMB", false, accentColor());
            }
            case CPS -> {
                SmoothHudRenderer.drawText(graphics, "CPS", x + 4, y + 7, 0xFF6D9FEA);
                SmoothHudRenderer.drawText(graphics, "L 0", x + 34, y + 7, 0xFFE9F1FF);
                SmoothHudRenderer.drawText(graphics, "R 0", x + 65, y + 7, 0xFFE9F1FF);
                SmoothHudRenderer.drawRoundedRect(graphics, x + 4, y + 24, 92, 2, 1, accentColor());
            }
            case ITEMS -> {
                ItemStack[] icons = {
                        new ItemStack(Items.TOTEM_OF_UNDYING),
                        new ItemStack(Items.GOLDEN_APPLE),
                        new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
                        new ItemStack(Items.ENDER_PEARL),
                        new ItemStack(Items.END_CRYSTAL),
                        new ItemStack(Items.OBSIDIAN)
                };
                for (int i = 0; i < icons.length; i++) {
                    int itemX = x + i * 47;
                    panel(graphics, itemX, y + 2, 43, 24, 0xA60D121A);
                    graphics.renderItem(icons[i], itemX + 3, y + 5);
                    text(graphics, "0", itemX + 28, y + 9, 0xFF8993A2);
                }
            }
            case ARMOR -> {
                text(graphics, "ARMOR", x + 8, y + 4);
                text(graphics, "H   C   L   B", x + 12, y + 21, accentColor());
            }
            case EFFECTS -> {
                text(graphics, "ACTIVE EFFECTS", x + 9, y + 6);
                text(graphics, "Speed II          1:24", x + 9, y + 27);
                text(graphics, "Strength           0:58", x + 9, y + 51);
                text(graphics, "Regeneration     0:12", x + 9, y + 75);
            }
            case MEDIA -> {
                int density = SmoothHudRenderer.mediaTextureDensity();
                int previewHeight = hovered ? widgetHeight : 38;
                SmoothHudRenderer.drawMediaPanel(graphics, x, y, widgetWidth, previewHeight,
                        backgroundColor(), density);
                int textLeft = x + (hovered ? 62 : 16);
                if (hovered) {
                    SmoothHudRenderer.drawMediaTile(graphics, x + 10, y + 10, 40,
                            backgroundColor(), accentColor(), density);
                    SmoothHudRenderer.drawMediaControl(
                            graphics, x + 110, y + 28, 0, false, false, accentColor(), density);
                    SmoothHudRenderer.drawMediaControl(
                            graphics, x + 136, y + 28, 1, true, false, accentColor(), density);
                    SmoothHudRenderer.drawMediaControl(
                            graphics, x + 162, y + 28, 2, false, false, accentColor(), density);
                }
                SmoothHudRenderer.drawMediaText(graphics, "Song title", textLeft, y + 6, 0xFFF4F6FA, density);
                SmoothHudRenderer.drawMediaText(graphics, "Artist", textLeft, y + 18, MUTED, density);
                int progressLeft = x + (hovered ? 62 : 16);
                SmoothHudRenderer.drawRoundedRect(graphics, progressLeft, y + previewHeight - 4,
                        x + widgetWidth - (hovered ? 12 : 16) - progressLeft,
                        2, 1, accentColor());
            }
            case TARGET -> {
                text(graphics, "Target player", x + 10, y + 8);
                text(graphics, "5.2 blocks  |  18 / 20 HP", x + 10, y + 22);
            }
            case REACH -> text(graphics, "Reach  3.42 blocks", x + 8, y + 9);
            case COMBO -> text(graphics, "Combo  x3", x + 8, y + 10);
        }
        if (widget != HudLayout.Widget.MEDIA) {
            graphics.drawString(font, UiText.tr(widget.name()), x + 7, y + widgetHeight - 11, MUTED, false);
        }
        if (scale != 1.0f) graphics.pose().popMatrix();
        SmoothHudRenderer.drawRoundedRect(graphics,
                x + HudLayout.width(config, widget) - 10,
                y + HudLayout.height(config, widget) - 10, 10, 10, 3, accentColor());
        graphics.drawString(font, Math.round(scale * 100) + "%",
                x + HudLayout.width(config, widget) - 34,
                y + HudLayout.height(config, widget) - 7, 0xFFFFFFFF, false);
    }

    private int x(HudLayout.Widget widget) {
        return HudLayout.x(config, widget, width, height);
    }

    private int y(HudLayout.Widget widget) {
        return HudLayout.y(config, widget, width, height);
    }

    private void text(GuiGraphics graphics, String value, int x, int y) {
        text(graphics, UiText.tr(value), x, y, 0xFFF4F6FA);
    }

    private void text(GuiGraphics graphics, String value, int x, int y, int color) {
        graphics.drawString(font, UiText.tr(value), x, y, color, false);
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        SmoothHudRenderer.drawRoundedRect(graphics, x, y, width, height, 7, color);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int backgroundColor() {
        return (ModConfig.parseColor(config.uiBackgroundColor, 0xFF090B12) & 0x00FFFFFF) | 0xB0000000;
    }

    private int accentColor() {
        return ModConfig.parseColor(config.uiAccentColor, 0xFF3478F6);
    }

    private static int blend(int base, int tint, float amount) {
        int red = Math.round(((base >>> 16) & 0xFF) * (1 - amount) + ((tint >>> 16) & 0xFF) * amount);
        int green = Math.round(((base >>> 8) & 0xFF) * (1 - amount) + ((tint >>> 8) & 0xFF) * amount);
        int blue = Math.round((base & 0xFF) * (1 - amount) + (tint & 0xFF) * amount);
        return (base & 0xFF000000) | red << 16 | green << 8 | blue;
    }
}
