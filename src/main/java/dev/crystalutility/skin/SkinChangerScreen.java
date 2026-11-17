package dev.crystalutility.skin;

import dev.crystalutility.config.ModConfig;
import dev.wvlib.api.ThemeColors;
import dev.crystalutility.render.ThemedUiRenderer;
import dev.crystalutility.text.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class SkinChangerScreen extends Screen {
    private static final int MUTED = 0xFF99A5B5;
    private static final int CARD = 0xB8202630;
    private static final int PANEL = 0xD8151A22;
    private static final int ROW_HEIGHT = 72;
    private final Screen parent;
    private EditBox profileNameInput;
    private String profileName = "";
    private boolean fetchingProfileSkin;
    private boolean openingFilePicker;
    private String message = "";
    private int scrollOffset;
    private List<String> skins = List.of();
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private int previewX;
    private int previewWidth;

    public SkinChangerScreen(Screen parent) {
        super(Component.literal(UiText.tr("Skin Studio")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientSkinManager.releaseSkinThumbnails(Minecraft.getInstance());
        panelWidth = Math.min(960, width - 24);
        panelHeight = Math.min(560, height - 24);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int sideWidth = Math.max(76, Math.min(112, panelWidth / 8));
        previewWidth = Math.max(210, panelWidth * 34 / 100);
        listX = panelX + sideWidth + 14;
        previewX = panelX + panelWidth - previewWidth - 14;
        listWidth = previewX - listX - 12;
        listY = panelY + 72;
        listHeight = panelHeight - 142;
        skins = ClientSkinManager.skinLibrary();
        scrollOffset = Math.min(scrollOffset, maxScroll());
        int profileFieldX = previewX + 14;
        int profileFieldY = panelY + 66 + 38;
        int profileButtonWidth = 78;
        int profileFieldWidth = Math.max(80, previewWidth - 42 - profileButtonWidth);
        profileNameInput = new EditBox(font, profileFieldX, profileFieldY, profileFieldWidth, 20,
                Component.literal(UiText.tr("Minecraft username")));
        profileNameInput.setMaxLength(16);
        profileNameInput.setValue(profileName);
        addRenderableWidget(profileNameInput);
        ClientSkinManager.beginPreview();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int accent = ThemeColors.accent(ModConfig.get().uiAccentColor);
        ThemedUiRenderer.drawScreenBackdrop(graphics, this, 0xA8080C13);
        roundedPanel(graphics, panelX, panelY, panelWidth, panelHeight, 0xD811151C);
        graphics.fill(panelX + 2, panelY + 2, panelX + panelWidth - 2, panelY + 4, accent);

        graphics.drawString(font, UiText.tr("WARDROBE"), panelX + 20, panelY + 19, 0xFFF3F6FA, false);
        graphics.drawString(font, UiText.tr("SKINS  /  %s", skins.size()), listX, panelY + 20, 0xFFDDE4ED, false);
        graphics.drawString(font, UiText.tr("Your local skin library"), listX, panelY + 38, MUTED, false);
        drawButton(graphics, panelX + panelWidth - 130, panelY + 14, 112, 28,
                UiText.tr(openingFilePicker ? "Opening..." : "+ Add Skin"),
                mouseX, mouseY, accent, !openingFilePicker);

        drawSidebar(graphics, panelX + 12, panelY + 66, accent);
        drawSkinLibrary(graphics, mouseX, mouseY, accent);
        drawPreview(graphics, mouseX, mouseY, partialTick, accent);
        drawFooter(graphics, mouseX, mouseY, accent);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double mouseX = event.x();
        double mouseY = event.y();
        if (inside(mouseX, mouseY, panelX + panelWidth - 130, panelY + 14, 112, 28)) {
            chooseSkin();
            return true;
        }
        int profileFetchX = previewX + previewWidth - 14 - 78;
        int profileFetchY = panelY + 66 + 38;
        if (inside(mouseX, mouseY, profileFetchX, profileFetchY, 78, 20)) {
            fetchProfileSkin();
            return true;
        }
        if (inside(mouseX, mouseY, listX, listY, listWidth, listHeight)) {
            int columns = columns();
            int cellWidth = (listWidth - 10 - (columns - 1) * 8) / columns;
            int relativeX = (int) mouseX - listX - 5;
            int relativeY = (int) mouseY - listY - 5;
            if (relativeX >= 0 && relativeY >= 0) {
                int column = relativeX / (cellWidth + 8);
                int row = relativeY / ROW_HEIGHT;
                int index = scrollOffset + row * columns + column;
                if (column < columns && index >= 0 && index < skins.size()) {
                    ClientSkinManager.selectSkin(Minecraft.getInstance(), skins.get(index));
                    message = "";
                    return true;
                }
            }
            return true;
        }
        int footerY = panelY + panelHeight - 48;
        if (inside(mouseX, mouseY, panelX + 16, footerY, 116, 30)) {
            applySkin();
            return true;
        }
        if (inside(mouseX, mouseY, panelX + 140, footerY, 100, 30)) {
            ClientSkinManager.setSlimModel(Minecraft.getInstance(), !ClientSkinManager.isSlimModel());
            return true;
        }
        if (inside(mouseX, mouseY, panelX + 248, footerY, 116, 30)) {
            ClientSkinManager.removeSkin(Minecraft.getInstance());
            message = "";
            return true;
        }
        if (inside(mouseX, mouseY, panelX + panelWidth - 104, footerY, 88, 30)) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inside(mouseX, mouseY, listX, listY, listWidth, listHeight)) {
            scrollOffset = Math.max(0, Math.min(maxScroll(),
                    scrollOffset - (int) Math.signum(verticalAmount)));
            ClientSkinManager.releaseSkinThumbnails(Minecraft.getInstance());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        ClientSkinManager.endPreview();
        ClientSkinManager.releaseSkinThumbnails(Minecraft.getInstance());
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        ClientSkinManager.endPreview();
        ClientSkinManager.releaseSkinThumbnails(Minecraft.getInstance());
    }

    private void drawSidebar(GuiGraphics graphics, int x, int y, int accent) {
        int sidebarWidth = listX - x - 8;
        graphics.fill(x, y, x + sidebarWidth, panelY + panelHeight - 56, PANEL);
        graphics.fill(x + 7, y + 8, x + sidebarWidth - 7, y + 39, 0xFF252D39);
        graphics.fill(x + 7, y + 8, x + 9, y + 39, accent);
        graphics.drawString(font, UiText.tr("Skins"), x + 17, y + 18, 0xFFFFFFFF, false);
    }

    private void drawSkinLibrary(GuiGraphics graphics, int mouseX, int mouseY, int accent) {
        graphics.fill(listX - 4, listY - 4, listX + listWidth + 4, listY + listHeight + 4, PANEL);
        if (skins.isEmpty()) {
            graphics.drawCenteredString(font, UiText.tr("Your wardrobe is empty"), listX + listWidth / 2,
                    listY + listHeight / 2 - 12, 0xFFE2E8F0);
            graphics.drawCenteredString(font, UiText.tr("Add a 64x64 PNG skin to get started"),
                    listX + listWidth / 2, listY + listHeight / 2 + 6, MUTED);
            return;
        }
        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
        int columns = columns();
        int cellWidth = (listWidth - 10 - (columns - 1) * 8) / columns;
        String selected = ClientSkinManager.selectedSkinName();
        int visibleRows = Math.max(1, (listHeight - 10) / ROW_HEIGHT + 1);
        for (int row = 0; row < visibleRows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = scrollOffset + row * columns + column;
                if (index >= skins.size()) break;
                String name = skins.get(index);
                int x = listX + 5 + column * (cellWidth + 8);
                int y = listY + 5 + row * ROW_HEIGHT;
                boolean active = name.equals(selected);
                boolean hovered = inside(mouseX, mouseY, x, y, cellWidth, ROW_HEIGHT - 8);
                roundedPanel(graphics, x, y, cellWidth, ROW_HEIGHT - 8,
                        active ? 0xFF293547 : hovered ? 0xFF252D39 : CARD);
                if (active) graphics.fill(x, y + 7, x + 2, y + ROW_HEIGHT - 15, accent);
                int iconSize = Math.min(42, ROW_HEIGHT - 24);
                roundedPanel(graphics, x + 9, y + 10, iconSize, iconSize,
                        active ? (accent & 0x00FFFFFF) | 0x33000000 : 0xFF303947);
                ClientSkinManager.drawSkinThumbnail(Minecraft.getInstance(), graphics, name,
                        x + 9, y + 10, iconSize);
                graphics.drawString(font, truncate(name, Math.max(8, cellWidth - iconSize - 28)),
                        x + iconSize + 17, y + 22, active ? 0xFFFFFFFF : 0xFFE0E6EE, false);
                graphics.drawString(font, UiText.tr(active ? "Selected" : "Local skin"),
                        x + iconSize + 17, y + 39, MUTED, false);
            }
        }
        graphics.disableScissor();
        if (maxScroll() > 0) {
            graphics.drawString(font, (scrollOffset / columns() + 1) + " / "
                            + Math.max(1, (skins.size() + columns() - 1) / columns()),
                    listX + listWidth - 44, listY + listHeight + 8, MUTED, false);
        }
    }

    private void drawPreview(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int accent) {
        int y = panelY + 66;
        int height = panelHeight - 122;
        graphics.fill(previewX, y, previewX + previewWidth, y + height, 0xFF0D1118);
        graphics.fill(previewX, y, previewX + previewWidth, y + 1, 0xFF343D4A);
        graphics.drawString(font, UiText.tr("PREVIEW"), previewX + 14, y + 13, 0xFFB8C3D1, false);
        drawButton(graphics, previewX + previewWidth - 14 - 78, y + 38, 78, 20,
                UiText.tr(fetchingProfileSkin ? "Loading..." : "Load Skin"), mouseX, mouseY, accent, !fetchingProfileSkin);
        String selected = ClientSkinManager.selectedSkinName();
        graphics.drawCenteredString(font, selected.isBlank() ? UiText.tr("Choose a skin") : truncate(selected, previewWidth - 26),
                previewX + previewWidth / 2, y + 64, 0xFFF2F5F9);
        Minecraft client = Minecraft.getInstance();
        if (!selected.isBlank()) {
            ClientSkinManager.drawAccountAvatar(client, graphics,
                    previewX + 18, y + 86, previewX + previewWidth - 18, y + height - 58,
                    52, mouseX, mouseY, partialTick);
        } else {
            graphics.drawCenteredString(font, UiText.tr("Add or select a skin"), previewX + previewWidth / 2,
                    y + height / 2 + 22, MUTED);
        }
        graphics.fill(previewX + 14, y + height - 54, previewX + previewWidth - 14,
                y + height - 53, 0xFF343D4A);
        graphics.drawCenteredString(font, ClientSkinManager.countdown(),
                previewX + previewWidth / 2, y + height - 42, 0xFFD8E0EA);
        graphics.fill(previewX + 14, y + height - 26,
                previewX + 14 + (ClientSkinManager.isSlimModel() ? 44 : 62), y + height - 23, accent);
        graphics.drawString(font, UiText.tr(ClientSkinManager.isSlimModel() ? "SLIM MODEL" : "CLASSIC MODEL"),
                previewX + 14, y + height - 17, MUTED, false);
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY, int accent) {
        int y = panelY + panelHeight - 48;
        graphics.fill(panelX + 14, y - 9, panelX + panelWidth - 14, y - 8, 0xFF343D4A);
        drawButton(graphics, panelX + 16, y, 116, 30, UiText.tr("Apply Skin"),
                mouseX, mouseY, accent, true);
        drawButton(graphics, panelX + 140, y, 100, 30,
                UiText.tr(ClientSkinManager.isSlimModel() ? "Slim  ✓" : "Classic  ✓"),
                mouseX, mouseY, accent, false);
        drawButton(graphics, panelX + 248, y, 116, 30, UiText.tr("Stop Wearing"),
                mouseX, mouseY, accent, false);
        graphics.drawString(font, message.isBlank() ? UiText.tr("Only you can see a local preview.")
                : truncate(message, Math.max(80, panelWidth - 500)),
                panelX + 376, y + 11, message.isBlank() ? MUTED : 0xFFE6C98A, false);
        drawButton(graphics, panelX + panelWidth - 104, y, 88, 30, UiText.tr("Done"),
                mouseX, mouseY, accent, false);
    }

    private void chooseSkin() {
        if (openingFilePicker) return;
        openingFilePicker = true;
        message = UiText.tr("Choose a PNG skin in the system file dialog.");
        Thread picker = new Thread(() -> {
            Path selected;
            try {
                selected = ClientSkinManager.choosePngFile();
            } catch (IOException exception) {
                Minecraft.getInstance().execute(() -> {
                    openingFilePicker = false;
                    message = exception.getMessage();
                });
                return;
            }
            Minecraft.getInstance().execute(() -> {
                openingFilePicker = false;
                if (selected == null) {
                    message = "";
                    return;
                }
                try {
                    ClientSkinManager.chooseSkin(Minecraft.getInstance(), selected);
                    skins = ClientSkinManager.skinLibrary();
                    scrollOffset = maxScroll();
                    message = UiText.tr("Skin added to your wardrobe.");
                } catch (IOException exception) {
                    message = exception.getMessage();
                }
            });
        }, "wvDoorClient-SkinFileDialog");
        picker.setDaemon(true);
        picker.start();
    }

    private void fetchProfileSkin() {
        if (fetchingProfileSkin) return;
        profileName = profileNameInput.getValue().trim();
        if (profileName.isBlank()) {
            message = UiText.tr("Enter the Minecraft username whose skin you want.");
            return;
        }
        fetchingProfileSkin = true;
        profileNameInput.setEditable(false);
        message = UiText.tr("Looking up %s's public Minecraft skin...", profileName);
        ClientSkinManager.fetchAndWearSkin(Minecraft.getInstance(), profileName)
                .whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
                    fetchingProfileSkin = false;
                    if (profileNameInput != null) profileNameInput.setEditable(true);
                    if (failure != null) {
                        Throwable cause = failure;
                        while (cause.getCause() != null) cause = cause.getCause();
                        message = cause.getMessage() == null ? UiText.tr("Could not fetch that player's skin.")
                                : cause.getMessage();
                        return;
                    }
                    skins = ClientSkinManager.skinLibrary();
                    scrollOffset = maxScroll();
                    message = result;
                }));
    }

    private void applySkin() {
        if (skins.isEmpty() || ClientSkinManager.selectedSkinName().isBlank()) {
            message = UiText.tr("Add a PNG skin before applying.");
            return;
        }
        ClientSkinManager.applySkin();
        message = UiText.tr("Applied locally. The 10-minute timer runs only while in a world.");
    }

    private int columns() {
        return listWidth >= 390 ? 2 : 1;
    }

    private int maxScroll() {
        int rows = (skins.size() + columns() - 1) / columns();
        int visibleRows = Math.max(1, (listHeight - 10) / ROW_HEIGHT);
        return Math.max(0, rows - visibleRows);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int width, int height, String label,
                            int mouseX, int mouseY, int accent, boolean primary) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        int fill = primary ? (hovered ? (accent & 0x00FFFFFF) | 0xE0000000
                        : (accent & 0x00FFFFFF) | 0xB0000000)
                : hovered ? 0xD02A3442 : 0xA8202630;
        roundedPanel(graphics, x, y, width, height, fill);
        if (!primary) {
            graphics.fill(x, y, x + width, y + 1, 0xFF3A4554);
            graphics.fill(x, y + height - 1, x + width, y + height, 0xFF3A4554);
        }
        graphics.drawCenteredString(font, label, x + width / 2, y + (height - 8) / 2,
                primary ? 0xFFFFFFFF : 0xFFE2E8F0);
    }

    private static void roundedPanel(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x + 2, y, x + width - 2, y + height, color);
        graphics.fill(x, y + 2, x + width, y + height - 2, color);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + ellipsis) > maxWidth) end--;
        return text.substring(0, end) + ellipsis;
    }
}
