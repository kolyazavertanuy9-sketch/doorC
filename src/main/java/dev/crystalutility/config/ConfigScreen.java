package dev.crystalutility.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import dev.crystalutility.render.ThemedUiRenderer;
import dev.crystalutility.render.SmoothHudRenderer;
import dev.crystalutility.render.UiAnimations;
import dev.crystalutility.social.FriendsScreen;
import dev.crystalutility.social.ScreenshotGalleryScreen;
import dev.crystalutility.text.UiText;

import java.util.List;

public final class ConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 53;
    private static final List<ThemePreset> THEME_PRESETS = List.of(
            new ThemePreset("Dark", "0B0F19", "0B0F19"),
            new ThemePreset("Light", "FDFBF7", "1A3A2B"),
            new ThemePreset("Orange", "1A1616", "FF5722")
    );
    private static final List<Tab> TABS = List.of(
            new Tab("Crystal", "Optimizer"),
            new Tab("Hitboxes", "Entity visuals"),
            new Tab("Combat", "Crosshair & target"),
            new Tab("PvP HUD", "On-screen widgets"),
            new Tab("Media", "Player overlay"),
            new Tab("Visuals", "World & effects"),
            new Tab("Movement", "Camera & controls"),
            new Tab("Theme", "Colors & style")
    );

    private final Screen parent;
    private final ModConfig config = ModConfig.get();
    private int selectedTab;
    private int scrollOffset;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;
    private int draggingSlider = -1;
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;
    private String pickerColorId = "";
    private int draggingColorChannel = -1;
    private long tabChangedAtNanos = System.nanoTime();
    private EditBox hexInput;
    private List<Setting> activeSettings = List.of();

    private ConfigScreen(Screen parent) {
        super(Component.literal(UiText.tr("wvDoorClient Settings")));
        this.parent = parent;
    }

    private void drawActionButton(GuiGraphics graphics, int x, int y, int buttonWidth, int buttonHeight,
                                  String label, boolean hovered) {
        roundedPanel(graphics, x, y, buttonWidth, buttonHeight, 8,
                hovered ? blend(accentColor(), 0xFFFFFFFF, 0.2f) : 0xFF202A39);
        SmoothHudRenderer.drawRoundedRect(graphics, x + 6, y + 1, buttonWidth - 12, 2, 1,
                withAlpha(0xFFFFFFFF, hovered ? 0x36 : 0x18));
        graphics.drawCenteredString(font, label, x + buttonWidth / 2, y + (buttonHeight - 8) / 2,
                textColor());
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha & 0xFF) << 24;
    }

    public static Screen create(Screen parent) {
        return new ConfigScreen(parent);
    }

    @Override
    protected void init() {
        super.init();
        panelWidth = Math.min(1040, width - 32);
        panelHeight = Math.min(640, height - 28);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int sidebarWidth = Math.min(208, Math.max(168, panelWidth / 5));
        contentX = panelX + sidebarWidth + 26;
        contentY = panelY + 78;
        contentWidth = panelWidth - sidebarWidth - 48;
        contentHeight = panelHeight - 134;
        activeSettings = settingsFor(selectedTab);
        scrollOffset = Math.min(scrollOffset, maxScroll());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ThemedUiRenderer.drawScreenBackdrop(graphics, this, 0xA8000000);
        roundedPanel(graphics, panelX, panelY, panelWidth, panelHeight, 12, panelColor());
        SmoothHudRenderer.drawRoundedRect(graphics, panelX + 12, panelY + 1,
                panelWidth - 24, 3, 1, withAlpha(accentColor(), 0xC0));
        drawHeader(graphics, mouseX, mouseY);
        drawSidebar(graphics, mouseX, mouseY);
        drawSettings(graphics, mouseX, mouseY);
        drawFooter(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!pickerColorId.isEmpty()) {
            drawColorPicker(graphics, mouseX, mouseY);
            if (hexInput != null) hexInput.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private void drawSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = panelX + 14;
        int y = panelY + 78;
        int tabWidth = contentX - panelX - 28;
        int sidebarHeight = panelHeight - 134;
        roundedPanel(graphics, x, y, tabWidth, sidebarHeight, 9, withPanelOpacity(0x7410141E));
        graphics.drawString(font, UiText.tr("CATEGORIES"), x + 13, y + 13, mutedTextColor(), false);
        graphics.enableScissor(x, y, x + tabWidth, y + sidebarHeight);
        for (int i = 0; i < TABS.size(); i++) {
            int top = y + 34 + i * tabStride();
            int itemHeight = tabHeight();
            boolean selected = i == selectedTab;
            boolean hovered = inside(mouseX, mouseY, x + 7, top, tabWidth - 14, itemHeight);
            if (selected || hovered) {
                roundedPanel(graphics, x + 7, top, tabWidth - 14, itemHeight, 7,
                        withPanelOpacity(selected ? 0xD51A2637 : 0x76232C3B));
            }
            if (selected) {
                float indicatorY = UiAnimations.approach("config-tab-indicator", top + 5,
                        top + 5, 100_000_000L);
                roundedPanel(graphics, x + 7, Math.round(indicatorY), 3, itemHeight - 10, 1, accentColor());
            }
            int badgeColor = selected ? accentColor() : 0xFF66748A;
            int badgeY = top + (itemHeight - 21) / 2;
            roundedPanel(graphics, x + 17, badgeY, 22, 21, 6,
                    selected ? withAlpha(accentColor(), 0x3D) : 0x55374355);
            graphics.drawCenteredString(font, Integer.toString(i + 1), x + 28, badgeY + 6,
                    badgeColor);
            int titleY = top + Math.max(1, (itemHeight - 18) / 2);
            graphics.drawString(font, UiText.tr(TABS.get(i).title), x + 48, titleY,
                    selected ? textColor() : blend(textColor(), mutedTextColor(), 0.3f), false);
            if (itemHeight >= 40) {
                graphics.drawString(font, UiText.tr(TABS.get(i).subtitle), x + 48, top + 25,
                        selected ? blend(textColor(), mutedTextColor(), 0.25f) : mutedTextColor(), false);
            }
        }
        graphics.disableScissor();
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        int dividerX = contentX - 14;
        SmoothHudRenderer.drawRoundedRect(graphics, dividerX, panelY + 14, 2,
                panelHeight - 72, 1, 0x333C4A5E);
        graphics.drawString(font, "wvDoorClient", panelX + 22, panelY + 20, textColor(), false);
        graphics.drawString(font, UiText.tr("CLIENT CONTROL CENTER"), panelX + 22, panelY + 38, mutedTextColor(), false);
        graphics.drawString(font, UiText.tr(TABS.get(selectedTab).title),
                contentX, panelY + 22, textColor(), false);
        graphics.drawString(font, UiText.tr(TABS.get(selectedTab).subtitle) + "  /  " + UiText.tr("Changes save automatically"),
                contentX, panelY + 42, mutedTextColor(), false);
        int layoutButtonX = panelX + panelWidth - 186;
        int layoutButtonY = panelY + 19;
        drawActionButton(graphics, layoutButtonX, panelY + 19, 166, 30,
                UiText.tr("Edit HUD layout"), inside(mouseX, mouseY, layoutButtonX, layoutButtonY, 166, 30));
        SmoothHudRenderer.drawRoundedRect(graphics, panelX + 14, panelY + 58,
                panelWidth - 28, 2, 1, 0x333C4A5E);
    }

    private void drawSettings(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
        float linearProgress = Math.min(1.0f,
                (float) (System.nanoTime() - tabChangedAtNanos) / 180_000_000L);
        float tabProgress = linearProgress * linearProgress * (3.0f - 2.0f * linearProgress);
        float slide = (1.0f - tabProgress) * 5.0f;
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, slide);
        for (int i = 0; i < activeSettings.size(); i++) {
            Setting setting = activeSettings.get(i);
            int y = rowY(i);
            if (y + ROW_HEIGHT < contentY || y > contentY + contentHeight) continue;
            boolean hovered = inside(mouseX, mouseY, contentX, y, contentWidth, ROW_HEIGHT - 4);
            float hover = UiAnimations.approach("config-hover-" + setting.id, hovered ? 1.0f : 0.0f,
                    hovered ? 1.0f : 0.0f, 130_000_000L);
            roundedPanel(graphics, contentX, y, contentWidth, ROW_HEIGHT - 5, 7,
                    withPanelOpacity(blend(0xB5151B25, 0xD51B2431, hover)));
            if (hover > 0.01f) roundedPanel(graphics, contentX, y + 10, 2, ROW_HEIGHT - 25, 1,
                    withAlpha(accentColor(), Math.round(0xCC * hover)));
            graphics.drawString(font, UiText.tr(setting.title), contentX + 15, y + 8,
                    textColor(), false);
            graphics.drawString(font, UiText.tr(setting.description), contentX + 15, y + 27,
                    mutedTextColor(), false);
            drawControl(graphics, setting, i, mouseX, mouseY, y);
        }
        if (selectedTab == 7) drawThemePresets(graphics, mouseX, mouseY);
        graphics.pose().popMatrix();
        graphics.disableScissor();
        drawScrollbar(graphics);
        if (maxScroll() > 0) {
            graphics.drawString(font, UiText.tr("SCROLL TO SEE MORE"),
                    contentX, panelY + panelHeight - 49, mutedTextColor(), false);
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        if (maxScroll() <= 0) return;
        int trackX = contentX + contentWidth - 4;
        int trackY = contentY + 4;
        int trackHeight = contentHeight - 8;
        int thumbHeight = scrollbarThumbHeight(trackHeight);
        int thumbY = scrollbarThumbY(trackY, trackHeight, thumbHeight);
        roundedPanel(graphics, trackX, trackY, 2, trackHeight, 1, 0x554A5668);
        roundedPanel(graphics, trackX - 1, thumbY, 4, thumbHeight, 2,
                withAlpha(accentColor(), 0xC0));
    }

    private int scrollbarThumbHeight(int trackHeight) {
        int totalRows = activeSettings.size() + (selectedTab == 7 ? 1 : 0);
        return Math.min(trackHeight, Math.max(24,
                Math.round(trackHeight * (contentHeight / (float) (totalRows * ROW_HEIGHT)))));
    }

    private int scrollbarThumbY(int trackY, int trackHeight, int thumbHeight) {
        int travel = trackHeight - thumbHeight;
        return trackY + (maxScroll() == 0 ? 0
                : Math.round(travel * scrollOffset / (float) maxScroll()));
    }

    private void updateScrollbar(double mouseY, int trackY, int trackHeight, int thumbHeight) {
        int travel = trackHeight - thumbHeight;
        if (travel <= 0) {
            scrollOffset = 0;
            return;
        }
        float progress = (float) Math.max(0, Math.min(1,
                (mouseY - scrollbarDragOffset - trackY) / travel));
        scrollOffset = Math.round(progress * maxScroll());
    }

    private void drawControl(GuiGraphics graphics, Setting setting, int index, int mouseX, int mouseY, int y) {
        int right = contentX + contentWidth - 12;
        if (setting.type == Type.TOGGLE) {
            boolean enabled = booleanValue(setting.id);
            int x = right - 48;
            float toggleProgress = UiAnimations.approach("config-toggle-" + setting.id,
                    enabled ? 1.0f : 0.0f, enabled ? 1.0f : 0.0f, 140_000_000L);
            roundedPanel(graphics, x, y + 14, 46, 22, 11,
                    blend(0xFF343D4B, withAlpha(accentColor(), 0xD8), toggleProgress));
            roundedPanel(graphics, x + 3 + Math.round(22 * toggleProgress), y + 17, 16, 16, 8,
                    blend(0xFFA8B2C1, 0xFFF4F8FF, toggleProgress));
        } else if (setting.type == Type.SLIDER) {
            int x = right - 204;
            int trackY = y + 22;
            float value = floatValue(setting.id);
            float fraction = Math.max(0, Math.min(1, (value - setting.min) / (setting.max - setting.min)));
            int trackWidth = 132;
            int fillWidth = Math.round(trackWidth * fraction);
            roundedPanel(graphics, x, trackY - 2, trackWidth, 6, 3, 0xFF353E4C);
            if (fillWidth > 0) roundedPanel(graphics, x, trackY - 2, fillWidth, 6, 3, accentColor());
            roundedPanel(graphics, x + fillWidth - 5, trackY - 6, 12, 14, 7, 0xFFF1F6FF);
            graphics.drawString(font, sliderValue(setting.id),
                    x + 143, y + 18, textColor(), false);
        } else if (setting.type == Type.COLOR) {
            int color = ModConfig.parseColor(colorValue(setting.id), 0xFFFFFFFF);
            int controlX = right - 146;
            roundedPanel(graphics, controlX, y + 12, 134, 27, 7,
                    inside(mouseX, mouseY, controlX, y + 12, 134, 27) ? 0xFF303A4A : 0xFF202733);
            roundedPanel(graphics, controlX + 5, y + 16, 19, 19, 5, 0xFF778394);
            roundedPanel(graphics, controlX + 7, y + 18, 15, 15, 4, color);
            graphics.drawString(font, "#" + rgbHex(color), controlX + 27, y + 20,
                    textColor(), false);
            graphics.drawString(font, UiText.tr("EDIT"), controlX + 101, y + 20, accentColor(), false);
        } else if (setting.type == Type.CHOICE) {
            String value = choiceValue(setting.id);
            roundedPanel(graphics, right - 124, y + 12, 112, 27, 7, 0xFF202733);
            graphics.drawCenteredString(font, value, right - 62, y + 19, textColor());
            graphics.drawString(font, "‹", right - 117, y + 19, accentColor(), false);
            graphics.drawString(font, "›", right - 22, y + 19, accentColor(), false);
        }
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = panelY + panelHeight - 42;
        graphics.fill(panelX + 14, y - 8, panelX + panelWidth - 14, y - 7, 0x553D4A5C);
        drawFooterButton(graphics, panelX + 18, y, 94, 25, UiText.tr("Screenshots"), mouseX, mouseY);
        drawFooterButton(graphics, panelX + 120, y, 80, 25, UiText.tr("Friends"), mouseX, mouseY);
        graphics.drawString(font, UiText.tr("RIGHT SHIFT  •  Open settings"), panelX + 208, y + 7, mutedTextColor(), false);
        int buttonX = panelX + panelWidth - 104;
        drawActionButton(graphics, buttonX, y, 84, 25, UiText.tr("Done"),
                inside(mouseX, mouseY, buttonX, y, 84, 25));
    }

    private void drawFooterButton(GuiGraphics graphics, int x, int y, int buttonWidth, int buttonHeight,
                                  String label, int mouseX, int mouseY) {
        roundedPanel(graphics, x, y, buttonWidth, buttonHeight, 7,
                inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight)
                        ? 0xFF303A4B : 0xFF202A39);
        graphics.drawCenteredString(font, label, x + buttonWidth / 2, y + 8, textColor());
    }

    private void drawThemePresets(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = rowY(activeSettings.size()) + 4;
        graphics.drawString(font, UiText.tr("Theme presets"), contentX + 2, y + 2, textColor(), false);
        int gap = 8;
        int buttonWidth = (contentWidth - gap * 2) / 3;
        int buttonY = y + 18;
        for (int i = 0; i < THEME_PRESETS.size(); i++) {
            ThemePreset preset = THEME_PRESETS.get(i);
            int x = contentX + i * (buttonWidth + gap);
            boolean selected = rgbHex(panelColor()).equals(preset.background)
                    && rgbHex(accentColor()).equals(preset.accent);
            boolean hovered = inside(mouseX, mouseY, x, buttonY, buttonWidth, 32);
            roundedPanel(graphics, x, buttonY, buttonWidth, 32, 5,
                    selected ? blend(accentColor(), 0xFFFFFFFF, 0.16f)
                            : hovered ? cardHoverColor() : cardColor());
            graphics.fill(x + 9, buttonY + 10, x + 20, buttonY + 21,
                    ModConfig.parseColor(preset.background, 0xFF0B0F19));
            graphics.fill(x + 12, buttonY + 13, x + 18, buttonY + 19,
                    ModConfig.parseColor(preset.accent, 0xFF0B0F19));
            graphics.drawString(font, UiText.tr(preset.name), x + 26, buttonY + 12,
                    textColor(), false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (!pickerColorId.isEmpty()) {
            if (event.button() == 0 && inside(mouseX, mouseY, pickerCloseX(), pickerCloseY(), 72, 23)) {
                closeColorPicker();
                return true;
            }
            if (event.button() == 0 && inside(mouseX, mouseY, pickerX() + 28, pickerY() + 86,
                    pickerWidth() - 56, 108)) {
                draggingColorChannel = Math.min(2, Math.max(0, ((int) mouseY - pickerY() - 86) / 36));
                updateColorChannel(draggingColorChannel, mouseX);
                return true;
            }
            if (hexInput != null && hexInput.mouseClicked(event, doubleClick)) return true;
            if (event.button() == 0 && !inside(mouseX, mouseY, pickerX(), pickerY(), pickerWidth(), pickerHeight())) {
                closeColorPicker();
                return true;
            }
            return true;
        }
        int layoutButtonX = panelX + panelWidth - 186;
        if (event.button() == 0 && inside(mouseX, mouseY, layoutButtonX,
                panelY + 19, 166, 30)) {
            Minecraft.getInstance().setScreen(new HudLayoutScreen(this));
            return true;
        }
        int footerY = panelY + panelHeight - 42;
        if (event.button() == 0 && inside(mouseX, mouseY, panelX + 18, footerY, 94, 25)) {
            Minecraft.getInstance().setScreen(new ScreenshotGalleryScreen(this));
            return true;
        }
        if (event.button() == 0 && inside(mouseX, mouseY, panelX + 120, footerY, 80, 25)) {
            Minecraft.getInstance().setScreen(new FriendsScreen(this));
            return true;
        }
        if (event.button() == 0 && inside(mouseX, mouseY, panelX + panelWidth - 104,
                panelY + panelHeight - 42, 84, 25)) {
            onClose();
            return true;
        }
        int sidebarWidth = Math.min(208, Math.max(168, panelWidth / 5));
        int sidebarX = panelX + 14;
        int tabStartY = panelY + 112;
        if (event.button() == 0 && mouseX >= sidebarX && mouseX < sidebarX + sidebarWidth
                && mouseY >= tabStartY && mouseY < tabStartY + TABS.size() * tabStride()) {
            int tab = (int) ((mouseY - tabStartY) / tabStride());
            if (tab >= 0 && tab < TABS.size() && tab != selectedTab) {
                selectedTab = tab;
                scrollOffset = 0;
                tabChangedAtNanos = System.nanoTime();
                clearWidgets();
                init();
            }
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0 || !inside(mouseX, mouseY, contentX, contentY, contentWidth, contentHeight)) {
            return false;
        }
        if (maxScroll() > 0 && inside(mouseX, mouseY, contentX + contentWidth - 9,
                contentY, 9, contentHeight)) {
            int trackY = contentY + 4;
            int trackHeight = contentHeight - 8;
            int thumbHeight = scrollbarThumbHeight(trackHeight);
            int thumbY = scrollbarThumbY(trackY, trackHeight, thumbHeight);
            if (mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
                draggingScrollbar = true;
                scrollbarDragOffset = (int) mouseY - thumbY;
            } else {
                scrollbarDragOffset = thumbHeight / 2;
                updateScrollbar(mouseY, trackY, trackHeight, thumbHeight);
                draggingScrollbar = true;
            }
            return true;
        }
        for (int i = 0; i < activeSettings.size(); i++) {
            int y = rowY(i);
            if (!inside(mouseX, mouseY, contentX, y, contentWidth, ROW_HEIGHT - 4)) continue;
            Setting setting = activeSettings.get(i);
            if (setting.type == Type.TOGGLE) {
                toggle(setting.id);
                ModConfig.save();
                return true;
            }
            if (setting.type == Type.COLOR) {
                int right = contentX + contentWidth - 12;
                if (inside(mouseX, mouseY, right - 146, y + 12, 134, 27)) {
                    pickerColorId = setting.id;
                    createHexInput();
                    return true;
                }
                return false;
            }
            if (setting.type == Type.SLIDER) {
                draggingSlider = i;
                updateSlider(setting, mouseX);
                return true;
            }
            if (setting.type == Type.CHOICE) {
                cycleChoice(setting.id);
                ModConfig.save();
                return true;
            }
        }
        if (selectedTab == 7) {
            int presetY = rowY(activeSettings.size()) + 22;
            int gap = 8;
            int buttonWidth = (contentWidth - gap * 2) / 3;
            for (int i = 0; i < THEME_PRESETS.size(); i++) {
                int x = contentX + i * (buttonWidth + gap);
                if (inside(mouseX, mouseY, x, presetY, buttonWidth, 32)) {
                    ThemePreset preset = THEME_PRESETS.get(i);
                    config.uiBackgroundColor = "FF" + preset.background;
                    config.uiAccentColor = "FF" + preset.accent;
                    config.uiTextColor = "FFF1F5FC";
                    config.uiMutedColor = "FF9BA9BB";
                    ModConfig.save();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (!pickerColorId.isEmpty()) {
            if (hexInput != null && hexInput.keyPressed(event)) return true;
            if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                    || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
                closeColorPicker();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingColorChannel >= 0 && event.button() == 0) {
            updateColorChannel(draggingColorChannel, event.x());
            return true;
        }
        if (draggingSlider >= 0 && event.button() == 0) {
            updateSlider(activeSettings.get(draggingSlider), event.x());
            return true;
        }
        if (draggingScrollbar && event.button() == 0) {
            int trackY = contentY + 4;
            int trackHeight = contentHeight - 8;
            updateScrollbar(event.y(), trackY, trackHeight, scrollbarThumbHeight(trackHeight));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingColorChannel >= 0 && event.button() == 0) {
            draggingColorChannel = -1;
            ModConfig.save();
            return true;
        }
        if (draggingSlider >= 0 && event.button() == 0) {
            draggingSlider = -1;
            ModConfig.save();
            return true;
        }
        if (draggingScrollbar && event.button() == 0) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inside(mouseX, mouseY, contentX, contentY, contentWidth, contentHeight)) {
            scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        closeColorPicker();
        ModConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }

    private void updateSlider(Setting setting, double mouseX) {
        int trackX = contentX + contentWidth - 216;
        float fraction = (float) Math.max(0, Math.min(1, (mouseX - trackX) / 132.0));
        float value = setting.min + fraction * (setting.max - setting.min);
        if (setting.id.equals("hitboxRange")) {
            config.hitboxRange = Math.round(value / 8.0f) * 8;
        } else if (setting.id.equals("timeOfDay")) {
            config.timeOfDay = Math.max(0, Math.min(23999, Math.round(value / 100.0f) * 100));
        } else {
            setFloat(setting.id, Math.round(value * 10.0f) / 10.0f);
        }
    }

    private void drawColorPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = pickerX();
        int y = pickerY();
        int width = pickerWidth();
        int height = pickerHeight();
        int color = ModConfig.parseColor(colorValue(pickerColorId), 0xFFFFFFFF);
        graphics.fill(0, 0, this.width, this.height, 0x99050910);
        roundedPanel(graphics, x, y, width, height, 9, panelColor());
        SmoothHudRenderer.drawRoundedRect(graphics, x + 12, y + 1, width - 24, 3, 1, accentColor());

        String title = activeSettings.stream().filter(setting -> setting.id.equals(pickerColorId))
                .map(Setting::title).findFirst().orElse("Choose color");
        graphics.drawString(font, UiText.tr(title), x + 18, y + 14, textColor(), false);
        graphics.drawString(font, UiText.tr("RGB color controls"), x + 18, y + 31, mutedTextColor(), false);
        SmoothHudRenderer.drawRoundedRect(graphics, x + 18, y + 49, 40, 28, 5, 0xFF778394);
        SmoothHudRenderer.drawRoundedRect(graphics, x + 20, y + 51, 36, 24, 4, color);
        graphics.drawString(font, UiText.tr("HEX"), x + 69, y + 51, mutedTextColor(), false);

        for (int channel = 0; channel < 3; channel++) {
            int rowY = y + 86 + channel * 36;
            int value = channelValue(color, channel);
            int trackX = x + 44;
            int trackWidth = width - 104;
            graphics.drawString(font, channelLabel(channel), x + 18, rowY + 4,
                    channelColor(channel), false);
            SmoothHudRenderer.drawGradientRoundedRect(graphics, trackX, rowY + 3,
                    trackWidth, 8, 4, withChannel(color, channel, 0), withChannel(color, channel, 255));
            int handleX = trackX + Math.round(value * (trackWidth - 1) / 255.0f);
            SmoothHudRenderer.drawRoundedRect(graphics, handleX - 2, rowY, 5, 14, 2, 0xFFF1F4F8);
            graphics.drawString(font, String.format(java.util.Locale.ROOT, "%3d", value),
                    trackX + trackWidth + 8, rowY + 4, textColor(), false);
        }

        roundedPanel(graphics, pickerCloseX(), pickerCloseY(), 72, 23, 5,
                inside(mouseX, mouseY, pickerCloseX(), pickerCloseY(), 72, 23)
                        ? blend(accentColor(), 0xFFFFFFFF, 0.18f) : accentColor());
        graphics.drawCenteredString(font, UiText.tr("Done"), pickerCloseX() + 36, pickerCloseY() + 7, 0xFFFFFFFF);
        graphics.drawString(font, UiText.tr("Changes preview immediately"), x + 18, y + height - 19, mutedTextColor(), false);
    }

    private void updateColorChannel(int channel, double mouseX) {
        int trackX = pickerX() + 44;
        int trackWidth = pickerWidth() - 104;
        int value = Math.round((float) Math.max(0, Math.min(1, (mouseX - trackX) / (trackWidth - 1))) * 255);
        int color = ModConfig.parseColor(colorValue(pickerColorId), 0xFFFFFFFF);
        int updatedColor = withChannel(color, channel, value);
        setColor(pickerColorId, String.format(java.util.Locale.ROOT, "%08X", updatedColor));
        if (hexInput != null) hexInput.setValue("#" + rgbHex(updatedColor));
    }

    private void createHexInput() {
        clearWidgets();
        hexInput = new EditBox(font, pickerX() + 69, pickerY() + 61, 122, 20,
                Component.literal(UiText.tr("HEX color")));
        hexInput.setMaxLength(7);
        hexInput.setFilter(value -> value.matches("(?i)#?[0-9a-f]{0,6}"));
        hexInput.setHint(Component.literal("#RRGGBB"));
        hexInput.setValue("#" + rgbHex(ModConfig.parseColor(colorValue(pickerColorId), 0xFFFFFFFF)));
        hexInput.setResponder(value -> {
            String hex = value.startsWith("#") ? value.substring(1) : value;
            if (hex.matches("(?i)[0-9a-f]{6}")) setColor(pickerColorId, hex);
        });
        addRenderableWidget(hexInput);
    }

    private static int withChannel(int color, int channel, int value) {
        return switch (channel) {
            case 0 -> (color & 0xFF00FFFF) | value << 16;
            case 1 -> (color & 0xFFFF00FF) | value << 8;
            default -> (color & 0xFFFFFF00) | value;
        };
    }

    private static int channelValue(int color, int channel) {
        return switch (channel) {
            case 0 -> color >>> 16 & 0xFF;
            case 1 -> color >>> 8 & 0xFF;
            default -> color & 0xFF;
        };
    }

    private static int channelColor(int channel) {
        return switch (channel) {
            case 0 -> 0xFFFF7777;
            case 1 -> 0xFF77E08A;
            default -> 0xFF77AFFF;
        };
    }

    private static String channelLabel(int channel) {
        return switch (channel) {
            case 0 -> "R";
            case 1 -> "G";
            default -> "B";
        };
    }

    private static String rgbHex(int color) {
        return String.format(java.util.Locale.ROOT, "%06X", color & 0x00FFFFFF);
    }

    private void closeColorPicker() {
        if (pickerColorId.isEmpty()) return;
        pickerColorId = "";
        draggingColorChannel = -1;
        clearWidgets();
        hexInput = null;
        ModConfig.save();
    }

    private int pickerWidth() {
        return Math.min(360, width - 24);
    }

    private int pickerHeight() {
        return 242;
    }

    private int pickerX() {
        return (width - pickerWidth()) / 2;
    }

    private int pickerY() {
        return (height - pickerHeight()) / 2;
    }

    private int pickerCloseX() {
        return pickerX() + pickerWidth() - 90;
    }

    private int pickerCloseY() {
        return pickerY() + pickerHeight() - 33;
    }

    private int rowY(int index) {
        return contentY + index * ROW_HEIGHT - scrollOffset * ROW_HEIGHT;
    }

    private int maxScroll() {
        int extraRows = selectedTab == 7 ? 1 : 0;
        return Math.max(0, activeSettings.size() + extraRows - Math.max(1, contentHeight / ROW_HEIGHT));
    }

    private int tabStride() {
        return Math.min(50, Math.max(34, (panelHeight - 172) / TABS.size()));
    }

    private int tabHeight() {
        return Math.min(43, tabStride() - 2);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static void roundedPanel(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        SmoothHudRenderer.drawRoundedRect(graphics, x, y, width, height, radius, color);
    }

    private List<Setting> settingsFor(int tab) {
        return switch (tab) {
            case 0 -> List.of(
                    toggle("crystalOptimizationEnabled", "Optimize crystal rendering", "Reduce rendering work during fast crystal fights."),
                    toggle("anchorOptimizationEnabled", "Anchor optimizer", "Reduce the vanilla repeat-use delay for respawn anchors."),
                    toggle("removeCrystalParticles", "Remove explosion and smoke particles", "Suppress crystal blast particles for steadier frame rates."),
                    toggle("hideAttackedCrystalsImmediately", "Hide attacked crystals immediately", "Hide the crystal locally as soon as you hit it.")
            );
            case 1 -> List.of(
                    toggle("hitboxesEnabled", "Custom hitboxes", "Render entity hitboxes. Enabled by default; toggle with B."),
                    toggle("renderEyeLines", "Eye-level markers", "Show a short line across each entity's eye height."),
                    toggle("renderFacingVectors", "Facing direction", "Show which direction each entity is looking."),
                    color("hitboxColor", "Hitbox color", "ARGB color in hexadecimal format."),
                    color("eyeColor", "Eye marker color", "ARGB color in hexadecimal format."),
                    color("facingColor", "Facing vector color", "ARGB color in hexadecimal format."),
                    slider("lineThickness", "Line thickness", "Width used by hitbox and direction lines.", 0.5f, 5.0f),
                    slider("hitboxRange", "Render distance", "Maximum distance to draw entity hitboxes.", 8, 256)
            );
            case 2 -> List.of(
                    toggle("customCrosshairEnabled", "Custom crosshair", "Use the configurable PvP crosshair."),
                    slider("crosshairSize", "Crosshair length", "Length of each crosshair arm.", 2, 12),
                    slider("crosshairGap", "Center gap", "Space between the crosshair arms.", 1, 8),
                    slider("crosshairThickness", "Crosshair thickness", "Width of each crosshair arm.", 1, 4),
                    color("crosshairColor", "Default crosshair color", "ARGB color in hexadecimal format."),
                    color("crosshairTargetColor", "Target crosshair color", "Color used while aiming at an entity."),
                    toggle("targetHudEnabled", "Target HUD", "Show information for the entity you are aiming at."),
                    toggle("targetHitboxHighlightEnabled", "Highlight target hitbox", "Use a separate outline color for your target."),
                    color("targetHitboxColor", "Target outline color", "ARGB color in hexadecimal format.")
            );
            case 3 -> List.of(
                    toggle("keystrokesHudEnabled", "Keystrokes", "Display movement and jump key presses."),
                    toggle("cpsHudEnabled", "CPS counter", "Show left and right clicks per second."),
                    toggle("inventoryItemsHudEnabled", "PvP item counters", "Show counts for totems, apples, pearls, crystals, and obsidian."),
                    toggle("coordinatesHudEnabled", "Coordinates and direction", "Display your position and facing direction."),
                    toggle("armorHudEnabled", "Armor durability", "Show equipped armor and remaining durability."),
                    toggle("shieldModelColorEnabled", "Color the shield item", "Apply readiness colors to the shield item itself."),
                    color("shieldModelReadyColor", "Shield item ready color", "Tint the shield item when it is ready to block."),
                    color("shieldModelBrokenColor", "Shield item disabled color",
                            "Tint disabled shields; remote status is estimated from damage near a shield block."),
                    toggle("effectsHudEnabled", "Active effects", "Display your current potion effects.")
            );
            case 4 -> List.of(
                    toggle("showMediaHud", "Media HUD", "Show the currently playing system media."),
                    toggle("discordRichPresenceEnabled", "Discord Rich Presence",
                            "Show wvDoorClient as your Discord game activity.")
            );
            case 5 -> List.of(
                    toggle("antiDarknessEnabled", "AntiDarkness", "Remove the Darkness effect's world-darkening overlay."),
                    toggle("fullbrightEnabled", "Fullbright", "Brighten the client-side light map."),
                    toggle("timeChangerEnabled", "Time changer", "Override time of day on your client only."),
                    slider("timeOfDay", "Time of day", "Daytime in Minecraft ticks (0-23999).", 0, 23999),
                    toggle("weatherChangerEnabled", "Weather changer", "Override local weather visuals only."),
                    choice("weatherMode", "Weather mode", "Choose the weather shown by your client."),
                    toggle("particleChangerEnabled", "Particle changer", "Reduce non-critical particles locally."),
                    choice("particleMode", "Particle amount", "Choose normal, reduced, or minimal effects."),
                    toggle("motionBlurEnabled", "Motion blur", "Blend recent frames for a temporal motion-blur effect."),
                    slider("motionBlurStrength", "Blur strength", "Adjust the amount of previous-frame persistence.", 0.02f, 0.5f),
                    toggle("chunkAnimatorEnabled", "Chunk load fade", "Brief, low-cost accent fade when chunks load."),
                    toggle("blockHighlightEnabled", "Custom block highlight", "Draw a soft filled highlight around the block under your crosshair."),
                    color("blockHighlightColor", "Block highlight color", "ARGB color for the selected block highlight."),
                    toggle("blockOutlineEnabled", "Vanilla block outline", "Show the vanilla outline when the custom highlight is disabled."),
                    toggle("damageParticlesEnabled", "Damage particles", "Show smooth floating damage numbers above hurt entities."),
                    color("damageParticleColor", "Damage particle color", "ARGB color for floating damage numbers."),
                    toggle("blockLabelsEnabled", "Block labels", "Show saved labels and highlighted block markers."),
                    toggle("blockLabelHighlightEnabled", "Highlight marked blocks", "Draw a colored box around every saved label."),
                    color("blockLabelColor", "Marker color", "ARGB color in hexadecimal format.")
            );
            case 6 -> List.of(
                    toggle("freeLookEnabled", "Freelook", "Toggle camera-only look with V; player aim stays fixed."),
                    toggle("toggleSprintEnabled", "Toggle sprint", "Enable the Sprint toggle key in Controls."),
                    toggle("toggleSneakEnabled", "Toggle sneak", "Enable the Sneak toggle key in Controls."),
                    toggle("reachDisplayEnabled", "Reach display", "Display distance to your current crosshair hit."),
                    toggle("comboCounterEnabled", "Combo counter", "Count consecutive attacks on the same target.")
            );
            default -> List.of(
                    color("uiBackgroundColor", "Panel background", "Main settings window color (HEX)."),
                    color("uiAccentColor", "Accent color", "Highlights, active toggles, and slider color (HEX)."),
                    color("uiTextColor", "Primary text", "Main labels and headings across the settings screen."),
                    color("uiMutedColor", "Muted text", "Descriptions and secondary labels across the settings screen.")
            );
        };
    }

    private static Setting toggle(String id, String title, String description) {
        return new Setting(id, title, description, Type.TOGGLE, 0, 1);
    }

    private static Setting color(String id, String title, String description) {
        return new Setting(id, title, description, Type.COLOR, 0, 0);
    }

    private static Setting slider(String id, String title, String description, float min, float max) {
        return new Setting(id, title, description, Type.SLIDER, min, max);
    }

    private static Setting choice(String id, String title, String description) {
        return new Setting(id, title, description, Type.CHOICE, 0, 0);
    }

    private boolean booleanValue(String id) {
        return switch (id) {
            case "crystalOptimizationEnabled" -> config.crystalOptimizationEnabled;
            case "anchorOptimizationEnabled" -> config.anchorOptimizationEnabled;
            case "removeCrystalParticles" -> config.removeCrystalParticles;
            case "hideAttackedCrystalsImmediately" -> config.hideAttackedCrystalsImmediately;
            case "hitboxesEnabled" -> config.hitboxesEnabled;
            case "renderEyeLines" -> config.renderEyeLines;
            case "renderFacingVectors" -> config.renderFacingVectors;
            case "customCrosshairEnabled" -> config.customCrosshairEnabled;
            case "targetHudEnabled" -> config.targetHudEnabled;
            case "targetHitboxHighlightEnabled" -> config.targetHitboxHighlightEnabled;
            case "keystrokesHudEnabled" -> config.keystrokesHudEnabled;
            case "cpsHudEnabled" -> config.cpsHudEnabled;
            case "inventoryItemsHudEnabled" -> config.inventoryItemsHudEnabled;
            case "coordinatesHudEnabled" -> config.coordinatesHudEnabled;
            case "armorHudEnabled" -> config.armorHudEnabled;
            case "shieldModelColorEnabled" -> config.shieldModelColorEnabled;
            case "effectsHudEnabled" -> config.effectsHudEnabled;
            case "antiDarknessEnabled" -> config.antiDarknessEnabled;
            case "showMediaHud" -> config.showMediaHud;
            case "discordRichPresenceEnabled" -> config.discordRichPresenceEnabled;
            case "fullbrightEnabled" -> config.fullbrightEnabled;
            case "timeChangerEnabled" -> config.timeChangerEnabled;
            case "weatherChangerEnabled" -> config.weatherChangerEnabled;
            case "particleChangerEnabled" -> config.particleChangerEnabled;
            case "freeLookEnabled" -> config.freeLookEnabled;
            case "toggleSprintEnabled" -> config.toggleSprintEnabled;
            case "toggleSneakEnabled" -> config.toggleSneakEnabled;
            case "motionBlurEnabled" -> config.motionBlurEnabled;
            case "chunkAnimatorEnabled" -> config.chunkAnimatorEnabled;
            case "reachDisplayEnabled" -> config.reachDisplayEnabled;
            case "comboCounterEnabled" -> config.comboCounterEnabled;
            case "blockOutlineEnabled" -> config.blockOutlineEnabled;
            case "blockHighlightEnabled" -> config.blockHighlightEnabled;
            case "damageParticlesEnabled" -> config.damageParticlesEnabled;
            case "blockLabelsEnabled" -> config.blockLabelsEnabled;
            case "blockLabelHighlightEnabled" -> config.blockLabelHighlightEnabled;
            default -> false;
        };
    }

    private void toggle(String id) {
        switch (id) {
            case "crystalOptimizationEnabled" -> config.crystalOptimizationEnabled = !config.crystalOptimizationEnabled;
            case "anchorOptimizationEnabled" -> config.anchorOptimizationEnabled = !config.anchorOptimizationEnabled;
            case "removeCrystalParticles" -> config.removeCrystalParticles = !config.removeCrystalParticles;
            case "hideAttackedCrystalsImmediately" -> config.hideAttackedCrystalsImmediately = !config.hideAttackedCrystalsImmediately;
            case "hitboxesEnabled" -> config.hitboxesEnabled = !config.hitboxesEnabled;
            case "renderEyeLines" -> config.renderEyeLines = !config.renderEyeLines;
            case "renderFacingVectors" -> config.renderFacingVectors = !config.renderFacingVectors;
            case "customCrosshairEnabled" -> config.customCrosshairEnabled = !config.customCrosshairEnabled;
            case "targetHudEnabled" -> config.targetHudEnabled = !config.targetHudEnabled;
            case "targetHitboxHighlightEnabled" -> config.targetHitboxHighlightEnabled = !config.targetHitboxHighlightEnabled;
            case "keystrokesHudEnabled" -> config.keystrokesHudEnabled = !config.keystrokesHudEnabled;
            case "cpsHudEnabled" -> config.cpsHudEnabled = !config.cpsHudEnabled;
            case "inventoryItemsHudEnabled" -> config.inventoryItemsHudEnabled = !config.inventoryItemsHudEnabled;
            case "coordinatesHudEnabled" -> config.coordinatesHudEnabled = !config.coordinatesHudEnabled;
            case "armorHudEnabled" -> config.armorHudEnabled = !config.armorHudEnabled;
            case "shieldModelColorEnabled" -> config.shieldModelColorEnabled = !config.shieldModelColorEnabled;
            case "effectsHudEnabled" -> config.effectsHudEnabled = !config.effectsHudEnabled;
            case "antiDarknessEnabled" -> config.antiDarknessEnabled = !config.antiDarknessEnabled;
            case "showMediaHud" -> config.showMediaHud = !config.showMediaHud;
            case "discordRichPresenceEnabled" -> config.discordRichPresenceEnabled =
                    !config.discordRichPresenceEnabled;
            case "fullbrightEnabled" -> config.fullbrightEnabled = !config.fullbrightEnabled;
            case "timeChangerEnabled" -> config.timeChangerEnabled = !config.timeChangerEnabled;
            case "weatherChangerEnabled" -> config.weatherChangerEnabled = !config.weatherChangerEnabled;
            case "particleChangerEnabled" -> config.particleChangerEnabled = !config.particleChangerEnabled;
            case "freeLookEnabled" -> {
                config.freeLookEnabled = !config.freeLookEnabled;
                if (!config.freeLookEnabled) dev.crystalutility.FeatureState.disableFreeLook();
            }
            case "toggleSprintEnabled" -> config.toggleSprintEnabled = !config.toggleSprintEnabled;
            case "toggleSneakEnabled" -> config.toggleSneakEnabled = !config.toggleSneakEnabled;
            case "motionBlurEnabled" -> config.motionBlurEnabled = !config.motionBlurEnabled;
            case "chunkAnimatorEnabled" -> config.chunkAnimatorEnabled = !config.chunkAnimatorEnabled;
            case "reachDisplayEnabled" -> config.reachDisplayEnabled = !config.reachDisplayEnabled;
            case "comboCounterEnabled" -> config.comboCounterEnabled = !config.comboCounterEnabled;
            case "blockOutlineEnabled" -> config.blockOutlineEnabled = !config.blockOutlineEnabled;
            case "blockHighlightEnabled" -> config.blockHighlightEnabled = !config.blockHighlightEnabled;
            case "damageParticlesEnabled" -> config.damageParticlesEnabled = !config.damageParticlesEnabled;
            case "blockLabelsEnabled" -> config.blockLabelsEnabled = !config.blockLabelsEnabled;
            case "blockLabelHighlightEnabled" -> config.blockLabelHighlightEnabled = !config.blockLabelHighlightEnabled;
        }
    }

    private float floatValue(String id) {
        return switch (id) {
            case "lineThickness" -> config.lineThickness;
            case "crosshairSize" -> config.crosshairSize;
            case "crosshairGap" -> config.crosshairGap;
            case "crosshairThickness" -> config.crosshairThickness;
            case "motionBlurStrength" -> config.motionBlurStrength;
            case "timeOfDay" -> config.timeOfDay;
            default -> config.hitboxRange;
        };
    }

    private String sliderValue(String id) {
        if (id.equals("hitboxRange")) return config.hitboxRange + " blocks";
        if (id.equals("timeOfDay")) {
            int hours = (config.timeOfDay / 1000 + 6) % 24;
            return String.format(java.util.Locale.ROOT, "%02d:00", hours);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", floatValue(id));
    }

    private String choiceValue(String id) {
        if (id.equals("weatherMode")) {
            return switch (config.weatherMode) {
                case 1 -> UiText.tr("Clear");
                case 2 -> UiText.tr("Rain");
                case 3 -> UiText.tr("Storm");
                default -> UiText.tr("Vanilla");
            };
        }
        return switch (config.particleMode) {
            case 1 -> UiText.tr("Reduced");
            case 2 -> UiText.tr("Minimal");
            default -> UiText.tr("Normal");
        };
    }

    private void cycleChoice(String id) {
        if (id.equals("weatherMode")) {
            config.weatherMode = (config.weatherMode + 1) % 4;
        } else {
            config.particleMode = (config.particleMode + 1) % 3;
        }
    }

    private void setFloat(String id, float value) {
        switch (id) {
            case "lineThickness" -> config.lineThickness = value;
            case "crosshairSize" -> config.crosshairSize = value;
            case "crosshairGap" -> config.crosshairGap = value;
            case "crosshairThickness" -> config.crosshairThickness = value;
            case "motionBlurStrength" -> config.motionBlurStrength = value;
        }
    }

    private String colorValue(String id) {
        return switch (id) {
            case "hitboxColor" -> config.hitboxColor;
            case "eyeColor" -> config.eyeColor;
            case "facingColor" -> config.facingColor;
            case "crosshairColor" -> config.crosshairColor;
            case "crosshairTargetColor" -> config.crosshairTargetColor;
            case "targetHitboxColor" -> config.targetHitboxColor;
            case "shieldModelReadyColor" -> config.shieldModelReadyColor;
            case "shieldModelBrokenColor" -> config.shieldModelBrokenColor;
            case "blockLabelColor" -> config.blockLabelColor;
            case "blockHighlightColor" -> config.blockHighlightColor;
            case "damageParticleColor" -> config.damageParticleColor;
            case "uiBackgroundColor" -> config.uiBackgroundColor;
            case "uiTextColor" -> config.uiTextColor;
            case "uiMutedColor" -> config.uiMutedColor;
            default -> config.uiAccentColor;
        };
    }

    private void setColor(String id, String value) {
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (!hex.matches("(?i)[0-9a-f]{6}([0-9a-f]{2})?")) return;
        switch (id) {
            case "hitboxColor" -> config.hitboxColor = hex;
            case "eyeColor" -> config.eyeColor = hex;
            case "facingColor" -> config.facingColor = hex;
            case "crosshairColor" -> config.crosshairColor = hex;
            case "crosshairTargetColor" -> config.crosshairTargetColor = hex;
            case "targetHitboxColor" -> config.targetHitboxColor = hex;
            case "shieldModelReadyColor" -> config.shieldModelReadyColor = hex;
            case "shieldModelBrokenColor" -> config.shieldModelBrokenColor = hex;
            case "blockLabelColor" -> config.blockLabelColor = hex;
            case "blockHighlightColor" -> config.blockHighlightColor = hex;
            case "damageParticleColor" -> config.damageParticleColor = hex;
            case "uiBackgroundColor" -> config.uiBackgroundColor = hex;
            case "uiAccentColor" -> config.uiAccentColor = hex;
            case "uiTextColor" -> config.uiTextColor = hex;
            case "uiMutedColor" -> config.uiMutedColor = hex;
        }
    }

    private int panelColor() {
        return withPanelOpacity((ModConfig.parseColor(config.uiBackgroundColor, 0xFF090B12) & 0x00FFFFFF) | 0xE8000000);
    }

    private int accentColor() {
        return ModConfig.parseColor(config.uiAccentColor, 0xFF3478F6);
    }

    private int textColor() {
        return ModConfig.parseColor(config.uiTextColor, 0xFFF1F5FC);
    }

    private int mutedTextColor() {
        return ModConfig.parseColor(config.uiMutedColor, 0xFF9BA9BB);
    }

    private int withPanelOpacity(int color) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * ModConfig.UI_PANEL_OPACITY);
        return color & 0x00FFFFFF | alpha << 24;
    }

    private int cardColor() {
        return withPanelOpacity((blend(panelColor(), 0xFF263146, 0.32f) & 0x00FFFFFF) | 0xA8000000);
    }

    private int cardHoverColor() {
        return withPanelOpacity((blend(panelColor(), 0xFF34445F, 0.48f) & 0x00FFFFFF) | 0xC0000000);
    }

    private static int blend(int base, int tint, float amount) {
        int red = Math.round(((base >>> 16) & 0xFF) * (1 - amount) + ((tint >>> 16) & 0xFF) * amount);
        int green = Math.round(((base >>> 8) & 0xFF) * (1 - amount) + ((tint >>> 8) & 0xFF) * amount);
        int blue = Math.round((base & 0xFF) * (1 - amount) + (tint & 0xFF) * amount);
        return (base & 0xFF000000) | red << 16 | green << 8 | blue;
    }

    private enum Type {
        TOGGLE, SLIDER, COLOR, CHOICE
    }

    private record ThemePreset(String name, String background, String accent) {}
    private record Tab(String title, String subtitle) {}
    private record Setting(String id, String title, String description, Type type, float min, float max) {}
}
