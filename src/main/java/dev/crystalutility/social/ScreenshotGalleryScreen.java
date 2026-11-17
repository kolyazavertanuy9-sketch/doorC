package dev.crystalutility.social;

import com.mojang.blaze3d.platform.NativeImage;
import dev.crystalutility.config.ModConfig;
import dev.wvlib.api.ThemeColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import dev.crystalutility.text.UiText;
import dev.crystalutility.render.SmoothDynamicTexture;
import dev.crystalutility.render.SmoothHudRenderer;
import dev.crystalutility.render.ThemedUiRenderer;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class ScreenshotGalleryScreen extends Screen {
    private static final int LIST_WIDTH = 230;
    private static final int ROW_HEIGHT = 34;
    private static final long MAX_IMAGE_BYTES = 16L * 1024 * 1024;

    private final Screen parent;
    private final Path screenshotDirectory;
    private List<Path> screenshots = List.of();
    private int selectedIndex;
    private int scrollOffset;
    private Identifier previewId;
    private int previewWidth;
    private int previewHeight;
    private String previewError = "";
    private String statusMessage = "";
    private boolean statusIsError;

    public ScreenshotGalleryScreen(Screen parent) {
        super(Component.literal(UiText.tr("wvDoorClient Screenshots")));
        this.parent = parent;
        screenshotDirectory = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
    }

    @Override
    protected void init() {
        refresh();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ThemedUiRenderer.drawScreenBackdrop(graphics, this, 0xA8080C13);
        int panelWidth = Math.min(850, width - 28);
        int panelHeight = Math.min(500, height - 28);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int background = ThemeColors.background(ModConfig.get().uiBackgroundColor, 0xD0);
        int accent = ThemeColors.accent(ModConfig.get().uiAccentColor);
        roundedPanel(graphics, left, top, panelWidth, panelHeight, background);
        SmoothHudRenderer.drawRoundedRect(graphics, left + 14, top + 2,
                panelWidth - 28, 3, 1, accent);
        graphics.drawString(font, UiText.tr("Screenshots"), left + 18, top + 16, 0xFFF4F7FB, false);
        graphics.drawString(font, UiText.tr("Your Minecraft captures"), left + 18, top + 32, 0xFF9BA9BB, false);

        int listX = left + 14;
        int listY = top + 58;
        int listHeight = panelHeight - 112;
        SmoothHudRenderer.drawRoundedRect(graphics, listX, listY, LIST_WIDTH, listHeight, 10, 0xA80F141E);
        graphics.enableScissor(listX, listY, listX + LIST_WIDTH, listY + listHeight);
        int visibleRows = Math.max(1, listHeight / ROW_HEIGHT);
        for (int row = 0; row < visibleRows; row++) {
            int index = scrollOffset + row;
            if (index >= screenshots.size()) break;
            int rowY = listY + row * ROW_HEIGHT;
            boolean selected = index == selectedIndex;
            if (selected) {
                SmoothHudRenderer.drawRoundedRect(graphics, listX + 4, rowY + 2,
                        LIST_WIDTH - 8, ROW_HEIGHT - 4, 6, 0xA82A3548);
                SmoothHudRenderer.drawRoundedRect(graphics, listX + 4, rowY + 6, 2,
                        ROW_HEIGHT - 12, 1, accent);
            }
            graphics.drawString(font, truncate(screenshots.get(index).getFileName().toString(), LIST_WIDTH - 24),
                    listX + 12, rowY + 7, selected ? 0xFFFFFFFF : 0xFFD7DEE8, false);
            graphics.drawString(font, modifiedTime(screenshots.get(index)), listX + 12,
                    rowY + 20, 0xFF8995A6, false);
        }
        graphics.disableScissor();

        int previewX = listX + LIST_WIDTH + 16;
        int previewY = listY;
        int previewWidth = panelWidth - LIST_WIDTH - 44;
        int previewHeight = listHeight;
        SmoothHudRenderer.drawRoundedRect(graphics, previewX, previewY,
                previewWidth, previewHeight, 10, 0xA80B0F17);
        if (previewId != null && previewWidth > 0 && previewHeight > 0) {
            float scale = Math.min((float) (previewWidth - 20) / previewWidthOfImage(),
                    (float) (previewHeight - 20) / previewHeightOfImage());
            int drawWidth = Math.max(1, Math.round(previewWidthOfImage() * scale));
            int drawHeight = Math.max(1, Math.round(previewHeightOfImage() * scale));
            int drawX = previewX + (previewWidth - drawWidth) / 2;
            int drawY = previewY + (previewHeight - drawHeight) / 2;
            graphics.blit(RenderPipelines.GUI_TEXTURED, previewId, drawX, drawY,
                    0, 0, drawWidth, drawHeight, previewWidthOfImage(), previewHeightOfImage(),
                    previewWidthOfImage(), previewHeightOfImage());
        } else {
            String message = screenshots.isEmpty() ? UiText.tr("No screenshots found") : UiText.tr(previewError);
            graphics.drawCenteredString(font, message.isBlank() ? UiText.tr("Select a screenshot") : message,
                    previewX + previewWidth / 2, previewY + previewHeight / 2, 0xFFBCC7D8);
        }

        drawButton(graphics, left + 14, top + panelHeight - 43, 104, 26,
                UiText.tr("Open folder"), mouseX, mouseY, accent);
        drawButton(graphics, left + 126, top + panelHeight - 43, 94, 26,
                UiText.tr("Refresh"), mouseX, mouseY, accent);
        drawButton(graphics, left + 228, top + panelHeight - 43, 108, 26,
                UiText.tr("Copy image"), mouseX, mouseY, accent);
        graphics.drawString(font, screenshots.isEmpty() ? "0 captures" : (selectedIndex + 1)
                + " / " + screenshots.size(), left + 348, top + panelHeight - 34, 0xFF9BA9BB, false);
        if (!statusMessage.isEmpty()) {
            graphics.drawString(font, truncate(statusMessage, panelWidth - 470),
                    left + 430, top + panelHeight - 34,
                    statusIsError ? 0xFFFF8A8A : 0xFF72D99A, false);
        }
        drawButton(graphics, left + panelWidth - 104, top + panelHeight - 43, 88, 26,
                UiText.tr("Done"), mouseX, mouseY, accent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        int panelWidth = Math.min(850, width - 28);
        int panelHeight = Math.min(500, height - 28);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int listX = left + 14;
        int listY = top + 58;
        int listHeight = panelHeight - 112;
        if (inside(event.x(), event.y(), listX, listY, LIST_WIDTH, listHeight)) {
            int row = ((int) event.y() - listY) / ROW_HEIGHT;
            int index = scrollOffset + row;
            if (index >= 0 && index < screenshots.size()) select(index);
            return true;
        }
        if (inside(event.x(), event.y(), left + 14, top + panelHeight - 43, 104, 26)) {
            openFolder();
            return true;
        }
        if (inside(event.x(), event.y(), left + 126, top + panelHeight - 43, 94, 26)) {
            refresh();
            return true;
        }
        if (inside(event.x(), event.y(), left + 228, top + panelHeight - 43, 108, 26)) {
            copySelectedImage();
            return true;
        }
        if (inside(event.x(), event.y(), left + panelWidth - 104, top + panelHeight - 43, 88, 26)) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelHeight = Math.min(500, height - 28);
        int top = (height - panelHeight) / 2;
        int listY = top + 58;
        int listHeight = panelHeight - 112;
        if (inside(mouseX, mouseY, (width - Math.min(850, width - 28)) / 2 + 14,
                listY, LIST_WIDTH, listHeight)) {
            int visibleRows = Math.max(1, listHeight / ROW_HEIGHT);
            scrollOffset = Math.max(0, Math.min(Math.max(0, screenshots.size() - visibleRows),
                    scrollOffset - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        releasePreview();
        Minecraft.getInstance().setScreen(parent);
    }

    private void refresh() {
        try {
            Files.createDirectories(screenshotDirectory);
            try (Stream<Path> files = Files.list(screenshotDirectory)) {
                screenshots = files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString()
                                .toLowerCase(java.util.Locale.ROOT).endsWith(".png"))
                        .sorted(Comparator.comparing(this::lastModified).reversed())
                        .toList();
            }
            selectedIndex = Math.min(selectedIndex, Math.max(0, screenshots.size() - 1));
            int visibleRows = Math.max(1, (Math.min(500, height - 28) - 112) / ROW_HEIGHT);
            scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, screenshots.size() - visibleRows)));
            if (!screenshots.isEmpty()) select(selectedIndex);
            else {
                releasePreview();
                previewError = "";
            }
        } catch (IOException exception) {
            screenshots = List.of();
            releasePreview();
            previewError = UiText.tr("Could not read screenshot folder");
            System.err.println("[wvDoorClient] Could not list screenshots: " + exception.getMessage());
        }
    }

    private void select(int index) {
        selectedIndex = index;
        releasePreview();
        previewError = "";
        Path selected = screenshots.get(index);
        try {
            long size = Files.size(selected);
            if (size < 24 || size > MAX_IMAGE_BYTES) {
                previewError = UiText.tr("Image is larger than the 16 MiB preview limit");
                return;
            }
            byte[] bytes = Files.readAllBytes(selected);
            if (!isSupportedPngSize(bytes)) {
                previewError = UiText.tr("PNG dimensions exceed 8192 x 8192");
                return;
            }
            NativeImage image = NativeImage.read(bytes);
            previewWidth = image.getWidth();
            previewHeight = image.getHeight();
            previewId = Identifier.fromNamespaceAndPath("crystalutility",
                    "screenshots/preview_" + Integer.toUnsignedString(selected.toAbsolutePath().toString().hashCode()));
            SmoothDynamicTexture texture = new SmoothDynamicTexture(
                    () -> "wvDoorClient screenshot preview", image);
            Minecraft.getInstance().getTextureManager().register(previewId, texture);
        } catch (IOException | RuntimeException exception) {
            previewError = UiText.tr("Could not load screenshot preview");
            System.err.println("[wvDoorClient] Could not load screenshot " + selected + ": " + exception.getMessage());
        }
    }

    private boolean isSupportedPngSize(byte[] bytes) {
        if (bytes.length < 24 || bytes[0] != (byte) 0x89 || bytes[1] != 0x50
                || bytes[2] != 0x4E || bytes[3] != 0x47) return false;
        long imageWidth = readUnsignedInt(bytes, 16);
        long imageHeight = readUnsignedInt(bytes, 20);
        return imageWidth > 0 && imageHeight > 0 && imageWidth <= 8192 && imageHeight <= 8192;
    }

    private static long readUnsignedInt(byte[] bytes, int offset) {
        return ((long) bytes[offset] & 0xFF) << 24
                | ((long) bytes[offset + 1] & 0xFF) << 16
                | ((long) bytes[offset + 2] & 0xFF) << 8
                | ((long) bytes[offset + 3] & 0xFF);
    }

    private void releasePreview() {
        if (previewId == null) return;
        Minecraft.getInstance().getTextureManager().release(previewId);
        previewId = null;
        previewWidth = 0;
        previewHeight = 0;
    }

    private int previewWidthOfImage() {
        return Math.max(1, previewWidth);
    }

    private int previewHeightOfImage() {
        return Math.max(1, previewHeight);
    }

    private FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException exception) {
            System.err.println("[wvDoorClient] Could not read screenshot timestamp " + path
                    + ": " + exception.getMessage());
            return FileTime.fromMillis(0);
        }
    }

    private String modifiedTime(Path path) {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm")
                .withZone(java.time.ZoneId.systemDefault()).format(lastModified(path).toInstant());
    }

    private void openFolder() {
        try {
            if (!Desktop.isDesktopSupported()) throw new IOException("Desktop file browsing is unavailable");
            Desktop.getDesktop().open(screenshotDirectory.toFile());
        } catch (IOException | UnsupportedOperationException exception) {
            System.err.println("[wvDoorClient] Could not open screenshot folder: " + exception.getMessage());
        }
    }

    private void copySelectedImage() {
        if (screenshots.isEmpty() || selectedIndex < 0 || selectedIndex >= screenshots.size()) {
            showStatus(UiText.tr("Select a screenshot first"), true);
            return;
        }
        Path selected = screenshots.get(selectedIndex);
        showStatus(UiText.tr("Copying image to system clipboard..."), false);
        CompletableFuture.runAsync(() -> {
            String result;
            boolean error;
            try {
                long size = Files.size(selected);
                if (size < 24 || size > MAX_IMAGE_BYTES) {
                    throw new IOException("Image exceeds the 16 MiB limit");
                }
                copyImageToClipboard(selected);
                result = UiText.tr("Image copied to clipboard");
                error = false;
            } catch (IOException | InterruptedException | SecurityException exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                result = UiText.tr("Could not copy image: %s", exception.getMessage());
                error = true;
                System.err.println("[wvDoorClient] Could not copy screenshot " + selected
                        + " to clipboard: " + exception.getMessage());
            }
            String status = result;
            boolean failed = error;
            Minecraft.getInstance().execute(() -> showStatus(status, failed));
        });
    }

    private static void copyImageToClipboard(Path image) throws IOException, InterruptedException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String escapedPath = image.toAbsolutePath().toString().replace("'", "''");
            String script = "Add-Type -AssemblyName System.Windows.Forms; "
                    + "Add-Type -AssemblyName System.Drawing; "
                    + "$image = [System.Drawing.Image]::FromFile('" + escapedPath + "'); "
                    + "try { [System.Windows.Forms.Clipboard]::SetImage($image) } "
                    + "finally { $image.Dispose() }";
            runClipboardCommand(image, false, "powershell.exe", "-NoProfile", "-NonInteractive",
                    "-STA", "-WindowStyle", "Hidden", "-Command", script);
        } else if (os.contains("mac")) {
            String escapedPath = image.toAbsolutePath().toString()
                    .replace("\\", "\\\\").replace("\"", "\\\"");
            String script = "set the clipboard to (read (POSIX file \"" + escapedPath
                    + "\") as «class PNGf»)";
            runClipboardCommand(image, false, "osascript", "-e", script);
        } else if (os.contains("linux") || os.contains("unix")) {
            if (System.getenv("WAYLAND_DISPLAY") != null) {
                runClipboardCommand(image, true, "wl-copy", "--type", "image/png");
            } else {
                runClipboardCommand(image, true, "xclip", "-selection", "clipboard",
                        "-t", "image/png", "-i");
            }
        } else {
            throw new IOException("Image clipboard is not supported on this operating system");
        }
    }

    private static void runClipboardCommand(Path image, boolean writeImageToStdin, String... command)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            if (writeImageToStdin) {
                try (java.io.OutputStream output = process.getOutputStream()) {
                    Files.copy(image, output);
                }
            } else {
                process.getOutputStream().close();
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Clipboard command timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException(command[0] + " exited with code " + process.exitValue());
            }
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private void showStatus(String message, boolean error) {
        statusMessage = message;
        statusIsError = error;
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int buttonWidth, int buttonHeight,
                            String label, int mouseX, int mouseY, int accent) {
        int color = inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight)
                ? (blend(accent, 0xFFFFFFFF, 0.16f) & 0x00FFFFFF) | 0xD8000000
                : 0xA8202C40;
        roundedPanel(graphics, x, y, buttonWidth, buttonHeight, color);
        graphics.drawCenteredString(font, label, x + buttonWidth / 2, y + 9, 0xFFEAF0F6);
    }

    private void roundedPanel(GuiGraphics graphics, int x, int y, int buttonWidth, int buttonHeight, int color) {
        SmoothHudRenderer.drawRoundedRect(graphics, x, y, buttonWidth, buttonHeight,
                Math.min(buttonHeight / 2, 10), color);
    }

    private String truncate(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        while (!value.isEmpty() && font.width(value + "...") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "...";
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
