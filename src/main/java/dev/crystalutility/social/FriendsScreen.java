package dev.crystalutility.social;

import dev.crystalutility.config.ModConfig;
import dev.crystalutility.render.ThemedUiRenderer;
import dev.wvlib.api.ThemeColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import dev.crystalutility.text.UiText;

import java.util.List;

public final class FriendsScreen extends Screen {
    private static final int ROW_HEIGHT = 42;
    private final Screen parent;
    private EditBox nameInput;
    private String statusMessage = UiText.tr("Friends are saved locally on this device.");
    private boolean statusIsSuccess;
    private int scrollOffset;

    public FriendsScreen(Screen parent) {
        super(Component.literal(UiText.tr("wvDoorClient Friends")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(620, width - 32);
        int panelHeight = Math.min(390, height - 28);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        nameInput = new EditBox(font, left + 18, top + panelHeight - 43, panelWidth - 132, 22,
                Component.literal(UiText.tr("Minecraft username")));
        nameInput.setMaxLength(16);
        nameInput.setHint(Component.literal(UiText.tr("Minecraft username")));
        addRenderableWidget(nameInput);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ThemedUiRenderer.drawScreenBackdrop(graphics, this, 0xA8080C13);
        int panelWidth = Math.min(620, width - 32);
        int panelHeight = Math.min(390, height - 28);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int accent = ThemeColors.accent(ModConfig.get().uiAccentColor);
        int background = ThemeColors.background(ModConfig.get().uiBackgroundColor, 0xD0);
        graphics.fill(left + 2, top, left + panelWidth - 2, top + panelHeight, background);
        graphics.fill(left, top + 2, left + panelWidth, top + panelHeight - 2, background);
        graphics.fill(left + 2, top + 2, left + panelWidth - 2, top + 4, accent);
        graphics.drawString(font, UiText.tr("Friends"), left + 18, top + 16, 0xFFF4F7FB, false);
        graphics.drawString(font, UiText.tr("Personal list  /  online on this server"), left + 18, top + 33,
                0xFF9BA9BB, false);
        drawButton(graphics, left + panelWidth - 104, top + 14, 88, 26,
                UiText.tr("Done"), mouseX, mouseY, accent);

        int listX = left + 16;
        int listY = top + 58;
        int listWidth = panelWidth - 32;
        int listHeight = panelHeight - 126;
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xA80F141E);
        List<String> friends = FriendManager.all();
        List<String> online = onlinePlayers();
        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
        int visibleRows = Math.max(1, listHeight / ROW_HEIGHT);
        for (int row = 0; row < visibleRows; row++) {
            int index = scrollOffset + row;
            if (index >= friends.size()) break;
            int rowY = listY + row * ROW_HEIGHT;
            String friend = friends.get(index);
            boolean isOnline = online.stream().anyMatch(name -> name.equalsIgnoreCase(friend));
            if (row % 2 == 0) graphics.fill(listX + 4, rowY, listX + listWidth - 4,
                    rowY + ROW_HEIGHT, 0x331F2A39);
            graphics.fill(listX + 14, rowY + 16, listX + 20, rowY + 22,
                    isOnline ? 0xFF63D58A : 0xFF687384);
            graphics.drawString(font, friend, listX + 30, rowY + 9, 0xFFF0F3F8, false);
            graphics.drawString(font, UiText.tr(isOnline ? "Online on this server" : "Offline"),
                    listX + 30, rowY + 23, 0xFF9BA9BB, false);
            drawButton(graphics, listX + listWidth - 76, rowY + 8, 62, 25,
                    UiText.tr("Remove"), mouseX, mouseY, accent);
        }
        graphics.disableScissor();
        if (friends.isEmpty()) {
            graphics.drawCenteredString(font, UiText.tr("Your local friends list is empty."),
                    listX + listWidth / 2, listY + listHeight / 2 - 4, 0xFFBCC7D8);
        }

        graphics.drawString(font, statusMessage, left + 18, top + panelHeight - 62,
                statusIsSuccess ? 0xFF65D68A : 0xFF9BA9BB, false);
        drawButton(graphics, left + panelWidth - 104, top + panelHeight - 43, 88, 24,
                UiText.tr("Add"), mouseX, mouseY, accent);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;

        int panelWidth = Math.min(620, width - 32);
        int panelHeight = Math.min(390, height - 28);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        if (inside(event.x(), event.y(), left + panelWidth - 104, top + 14, 88, 26)) {
            onClose();
            return true;
        }
        int listX = left + 16;
        int listY = top + 58;
        int listWidth = panelWidth - 32;
        int listHeight = panelHeight - 126;
        if (inside(event.x(), event.y(), listX + listWidth - 82, listY, 82, listHeight)) {
            int row = ((int) event.y() - listY) / ROW_HEIGHT;
            int index = scrollOffset + row;
            List<String> friends = FriendManager.all();
            if (index >= 0 && index < friends.size()) {
                FriendManager.remove(friends.get(index));
                statusMessage = UiText.tr("Friend removed from your local list.");
                statusIsSuccess = false;
            }
            return true;
        }
        int inputY = top + panelHeight - 43;
        int addX = left + panelWidth - 104;
        if (inside(event.x(), event.y(), addX, inputY, 88, 24)) {
            addFriend();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (nameInput.isFocused() && event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            addFriend();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelHeight = Math.min(390, height - 28);
        int listY = (height - panelHeight) / 2 + 58;
        int listHeight = panelHeight - 126;
        int listX = (width - Math.min(620, width - 32)) / 2 + 16;
        if (inside(mouseX, mouseY, listX, listY, Math.min(620, width - 32) - 32, listHeight)) {
            int visibleRows = Math.max(1, listHeight / ROW_HEIGHT);
            scrollOffset = Math.max(0, Math.min(Math.max(0, FriendManager.all().size() - visibleRows),
                    scrollOffset - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void addFriend() {
        String name = nameInput.getValue().trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            statusMessage = UiText.tr("Enter a valid Minecraft username (1-16 letters, digits, underscores).");
            statusIsSuccess = false;
            return;
        }
        if (FriendManager.add(name)) {
            statusMessage = UiText.tr("Added %s to your local friends list.", name);
            statusIsSuccess = true;
            nameInput.setValue("");
            int panelHeight = Math.min(390, height - 28);
            int visibleRows = Math.max(1, (panelHeight - 126) / ROW_HEIGHT);
            scrollOffset = Math.max(0, FriendManager.all().size() - visibleRows);
        } else {
            statusMessage = UiText.tr("That friend is already in your list.");
            statusIsSuccess = false;
        }
    }

    private List<String> onlinePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return List.of();
        return client.level.players().stream().map(Player::getName)
                .map(name -> name.getString()).toList();
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int buttonWidth, int buttonHeight,
                            String label, int mouseX, int mouseY, int accent) {
        int color = inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight)
                ? (blend(accent, 0xFFFFFFFF, 0.16f) & 0x00FFFFFF) | 0xD8000000
                : 0xA8202C40;
        graphics.fill(x + 2, y, x + buttonWidth - 2, y + buttonHeight, color);
        graphics.fill(x, y + 2, x + buttonWidth, y + buttonHeight - 2, color);
        graphics.drawCenteredString(font, label, x + buttonWidth / 2, y + 9, 0xFFEAF0F6);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static int blend(int base, int tint, float amount) {
        int red = Math.round(((base >>> 16) & 0xFF) * (1 - amount) + ((tint >>> 16) & 0xFF) * amount);
        int green = Math.round(((base >>> 8) & 0xFF) * (1 - amount) + ((tint >>> 8) & 0xFF) * amount);
        int blue = Math.round((base & 0xFF) * (1 - amount) + (tint & 0xFF) * amount);
        return (base & 0xFF000000) | red << 16 | green << 8 | blue;
    }
}
