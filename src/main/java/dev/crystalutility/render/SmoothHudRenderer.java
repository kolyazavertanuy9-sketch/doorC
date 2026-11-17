package dev.crystalutility.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.crystalutility.CrystalUtilityClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SmoothHudRenderer {
    private static final int SUPERSAMPLE = 6;
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.BOLD, 60);
    private static final Font MEDIA_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 54);
    private static final int MAX_TEXTURES = 96;
    private static final Map<String, Texture> TEXTURES = new LinkedHashMap<>(32, 0.75f, true);
    private static long nextTextureId;

    private SmoothHudRenderer() {
    }

    public static void drawKey(GuiGraphics graphics, int x, int y, int width, int height,
                               String text, boolean pressed, int accent) {
        Texture background = keyTexture(width, height, pressed, accent);
        blit(graphics, background, x, y);

        Texture label = textTexture(text, pressed ? 0xFFFFFFFF : 0xFFDCE6F5);
        blit(graphics, label, x + (width - label.width) / 2, y + (height - label.height) / 2);
    }

    public static void drawText(GuiGraphics graphics, String text, int x, int y, int color) {
        blit(graphics, textTexture(text, color), x, y);
    }

    public static void drawCenteredText(
            GuiGraphics graphics, String text, int x, int y, int width, int height, int color) {
        Texture texture = textTexture(text, color);
        blit(graphics, texture, x + (width - texture.width) / 2,
                y + (height - texture.height) / 2);
    }

    public static void drawCenteredTextTinted(
            GuiGraphics graphics, String text, int x, int y, int width, int height, int tint) {
        Texture texture = textTexture(text, 0xFFFFFFFF);
        blit(graphics, texture, x + (width - texture.width) / 2,
                y + (height - texture.height) / 2, tint);
    }

    public static int textWidth(String text) {
        BufferedImage measureImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = measureImage.createGraphics();
        graphics.setFont(FONT);
        int width = Math.max(1,
                (graphics.getFontMetrics().stringWidth(text) + SUPERSAMPLE - 1) / SUPERSAMPLE + 2);
        graphics.dispose();
        measureImage.flush();
        return width;
    }

    public static String fitText(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (textWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        while (!text.isEmpty() && textWidth(text + ellipsis) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text.isEmpty() ? "" : text + ellipsis;
    }

    public static int mediaTextureDensity() {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        return Math.max(2, Math.min(4, (int) Math.ceil(scale)));
    }

    public static void drawMediaPanel(
            GuiGraphics graphics, int x, int y, int width, int height, int color, int density) {
        Texture texture = texture("media-panel:" + width + "x" + height + ":" + color + ":" + density,
                width, height, density,
                () -> createMediaPanelImage(width, height, color, density));
        blit(graphics, texture, x, y);
    }

    public static void drawMediaTile(
            GuiGraphics graphics, int x, int y, int size, int color, int accent, int density) {
        Texture texture = texture("media-tile:" + size + ":" + color + ":" + accent + ":" + density,
                size, size, density,
                () -> createMediaTileImage(size, color, accent, density));
        blit(graphics, texture, x, y);
    }

    public static void drawMediaText(
            GuiGraphics graphics, String text, int x, int y, int color, int density) {
        Texture texture = mediaTextTexture(text, color, density);
        blit(graphics, texture, x, y);
    }

    public static int mediaTextWidth(String text) {
        BufferedImage measureImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = measureImage.createGraphics();
        graphics.setFont(MEDIA_FONT);
        int width = (graphics.getFontMetrics().stringWidth(text) + SUPERSAMPLE - 1) / SUPERSAMPLE + 2;
        graphics.dispose();
        return width;
    }

    public static void drawRoundedPanel(
            GuiGraphics graphics, int x, int y, int width, int height, int color) {
        drawRoundedRect(graphics, x, y, width, height, height / 2, color);
    }

    public static void drawRoundedRect(
            GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        int safeRadius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        Texture texture = texture("rounded:" + width + "x" + height + ":" + safeRadius,
                width, height, () -> createRoundedRectImage(width, height, safeRadius));
        blit(graphics, texture, x, y, color);
    }

    public static void drawGradientRoundedRect(
            GuiGraphics graphics, int x, int y, int width, int height, int radius,
            int startColor, int endColor) {
        if (width <= 0 || height <= 0) return;
        int safeRadius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        String key = "rounded-gradient:" + width + "x" + height + ":" + safeRadius
                + ":" + startColor + ":" + endColor;
        Texture texture = texture(key, width, height,
                () -> createGradientRoundedRectImage(width, height, safeRadius, startColor, endColor));
        blit(graphics, texture, x, y);
    }

    public static void drawMediaControl(
            GuiGraphics graphics, int x, int y, int type, boolean playing,
            boolean hovered, int accent, int density) {
        drawMediaControl(graphics, x, y, type, playing, hovered, accent, density, 255);
    }

    public static void drawMediaControl(
            GuiGraphics graphics, int x, int y, int type, boolean playing,
            boolean hovered, int accent, int density, int alpha) {
        String key = "media-control:" + type + ":" + (type == 1 && playing) + ":"
                + hovered + ":" + accent + ":" + density;
        Texture texture = texture(key, 24, 24, density,
                () -> createMediaControlImage(type, playing, hovered, accent, density));
        blit(graphics, texture, x, y, ARGB.white(Math.max(0.0f, Math.min(1.0f, alpha / 255.0f))));
    }

    private static Texture keyTexture(int width, int height, boolean pressed, int accent) {
        String key = "key:" + width + "x" + height + ":" + pressed + ":" + accent;
        return texture(key, width, height, () -> createKeyImage(width, height, pressed, accent));
    }

    private static Texture textTexture(String text, int color) {
        if (text.isEmpty()) {
            return texture("text:empty:" + color, 1, 1,
                    () -> new BufferedImage(SUPERSAMPLE, SUPERSAMPLE, BufferedImage.TYPE_INT_ARGB));
        }

        BufferedImage measureImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measureGraphics = measureImage.createGraphics();
        measureGraphics.setFont(FONT);
        FontMetrics metrics = measureGraphics.getFontMetrics();
        int width = Math.max(1, (metrics.stringWidth(text) + SUPERSAMPLE - 1) / SUPERSAMPLE + 2);
        int height = Math.max(1, (metrics.getHeight() + SUPERSAMPLE - 1) / SUPERSAMPLE + 2);
        measureGraphics.dispose();

        String key = "text:" + color + ":" + text;
        return texture(key, width, height, () -> createTextImage(text, color, width, height));
    }

    private static Texture mediaTextTexture(String text, int color, int density) {
        if (text.isEmpty()) {
            return texture("media-text:empty:" + color + ":" + density, 1, 1, density,
                    () -> new BufferedImage(density, density, BufferedImage.TYPE_INT_ARGB));
        }

        BufferedImage measureImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measureGraphics = measureImage.createGraphics();
        measureGraphics.setFont(MEDIA_FONT);
        FontMetrics metrics = measureGraphics.getFontMetrics();
        int width = Math.max(1, (metrics.stringWidth(text) + SUPERSAMPLE - 1) / SUPERSAMPLE + 2);
        int height = Math.max(1, (metrics.getHeight() + SUPERSAMPLE - 1) / SUPERSAMPLE + 2);
        measureGraphics.dispose();

        String key = "media-text:" + color + ":" + density + ":" + text;
        return texture(key, width, height, density,
                () -> createMediaTextImage(text, color, width, height, density));
    }

    private static Texture texture(String key, int width, int height, ImageFactory factory) {
        return texture(key, width, height, SUPERSAMPLE, factory);
    }

    private static Texture texture(
            String key, int width, int height, int density, ImageFactory factory) {
        Texture cached = TEXTURES.get(key);
        if (cached != null) return cached;

        BufferedImage image = factory.create();
        if (image.getWidth() != width * density || image.getHeight() != height * density) {
            throw new IllegalStateException("HUD texture dimensions do not match its logical size.");
        }
        int textureWidth = image.getWidth();
        int textureHeight = image.getHeight();
        NativeImage nativeImage = toNativeImage(image);
        Identifier id = Identifier.fromNamespaceAndPath(CrystalUtilityClient.MOD_ID,
                "dynamic/smooth_hud_" + nextTextureId++);
        DynamicTexture dynamicTexture = new SmoothDynamicTexture(
                () -> "wvDoorClient smooth HUD", nativeImage);
        Minecraft.getInstance().getTextureManager().register(id, dynamicTexture);
        Texture texture = new Texture(id, width, height, textureWidth, textureHeight);
        TEXTURES.put(key, texture);

        if (TEXTURES.size() > MAX_TEXTURES) {
            String oldestKey = TEXTURES.keySet().iterator().next();
            Texture oldest = TEXTURES.remove(oldestKey);
            Minecraft.getInstance().getTextureManager().release(oldest.id);
        }
        return texture;
    }

    private static BufferedImage createKeyImage(int width, int height, boolean pressed, int accent) {
        BufferedImage highRes = new BufferedImage(width * SUPERSAMPLE, height * SUPERSAMPLE,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = highRes.createGraphics();
        configureAntialiasing(graphics);
        graphics.scale(SUPERSAMPLE, SUPERSAMPLE);

        int fill = pressed ? (accent & 0x00FFFFFF) | 0xC8000000 : 0x76101824;
        graphics.setColor(new Color(fill, true));
        graphics.fill(new RoundRectangle2D.Float(0.5f, 0.5f, width - 1, height - 1, 12, 12));
        graphics.setColor(new Color(pressed ? (accent & 0x00FFFFFF) | 0xBB000000 : 0x526F8199, true));
        graphics.setStroke(new java.awt.BasicStroke(0.75f));
        graphics.draw(new RoundRectangle2D.Float(0.875f, 0.875f, width - 1.75f, height - 1.75f,
                11, 11));
        graphics.dispose();

        return highRes;
    }

    private static BufferedImage createRoundedRectImage(int width, int height, int radius) {
        BufferedImage highRes = new BufferedImage(width * SUPERSAMPLE, height * SUPERSAMPLE,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = highRes.createGraphics();
        configureAntialiasing(graphics);
        graphics.scale(SUPERSAMPLE, SUPERSAMPLE);
        graphics.setColor(Color.WHITE);
        float arc = radius * 2.0f;
        graphics.fill(new RoundRectangle2D.Float(0, 0, width, height, arc, arc));
        graphics.dispose();
        return highRes;
    }

    private static BufferedImage createGradientRoundedRectImage(
            int width, int height, int radius, int startColor, int endColor) {
        BufferedImage highRes = new BufferedImage(width * SUPERSAMPLE, height * SUPERSAMPLE,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = highRes.createGraphics();
        configureAntialiasing(graphics);
        graphics.scale(SUPERSAMPLE, SUPERSAMPLE);
        float arc = radius * 2.0f;
        graphics.setClip(new RoundRectangle2D.Float(0, 0, width, height, arc, arc));
        graphics.setPaint(new GradientPaint(0, 0, new Color(startColor, true),
                width, 0, new Color(endColor, true)));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return highRes;
    }

    private static BufferedImage createMediaControlImage(
            int type, boolean playing, boolean hovered, int accent, int density) {
        BufferedImage highRes = new BufferedImage(24 * SUPERSAMPLE * density,
                24 * SUPERSAMPLE * density,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = highRes.createGraphics();
        configureAntialiasing(graphics);
        graphics.scale(SUPERSAMPLE * density, SUPERSAMPLE * density);
        if (hovered) {
            graphics.setColor(new Color((accent & 0x00FFFFFF) | 0x50000000, true));
            graphics.fill(new java.awt.geom.Ellipse2D.Float(1, 1, 22, 22));
        }

        graphics.setColor(new Color(hovered ? 0xFFFFFFFF : 0xFFE9EDF2, true));
        Path2D icon = new Path2D.Float();
        if (type == 0) {
            icon.moveTo(19, 6);
            icon.lineTo(11, 12);
            icon.lineTo(19, 18);
            icon.closePath();
            graphics.fill(icon);
            icon.reset();
            icon.moveTo(12, 6);
            icon.lineTo(4, 12);
            icon.lineTo(12, 18);
            icon.closePath();
            graphics.fill(icon);
        } else if (type == 2) {
            icon.moveTo(5, 6);
            icon.lineTo(13, 12);
            icon.lineTo(5, 18);
            icon.closePath();
            graphics.fill(icon);
            icon.reset();
            icon.moveTo(12, 6);
            icon.lineTo(20, 12);
            icon.lineTo(12, 18);
            icon.closePath();
            graphics.fill(icon);
        } else if (playing) {
            graphics.fill(new RoundRectangle2D.Float(7, 5, 3.5f, 14, 2, 2));
            graphics.fill(new RoundRectangle2D.Float(14, 5, 3.5f, 14, 2, 2));
        } else {
            icon.moveTo(8, 4);
            icon.lineTo(19, 12);
            icon.lineTo(8, 20);
            icon.closePath();
            graphics.fill(icon);
        }
        graphics.dispose();
        return downsample(highRes, 24 * density, 24 * density);
    }

    private static BufferedImage createMediaTileImage(int size, int color, int accent, int density) {
        BufferedImage highRes = new BufferedImage(size * density * SUPERSAMPLE,
                size * density * SUPERSAMPLE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = highRes.createGraphics();
        configureAntialiasing(graphics);
        graphics.scale(density * SUPERSAMPLE, density * SUPERSAMPLE);
        graphics.setColor(new Color(color, true));
        graphics.fill(new RoundRectangle2D.Float(0, 0, size, size, 10, 10));
        graphics.setColor(new Color((accent & 0x00FFFFFF) | 0x65000000, true));
        graphics.setStroke(new java.awt.BasicStroke(0.7f,
                java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        graphics.draw(new RoundRectangle2D.Float(0.5f, 0.5f, size - 1, size - 1, 9.5f, 9.5f));

        graphics.setColor(new Color((accent & 0x00FFFFFF) | 0x50000000, true));
        graphics.fill(new java.awt.geom.Ellipse2D.Float(5, 5, size - 10, size - 10));
        graphics.setColor(new Color(0xFFF0F4FA, true));
        graphics.fill(new RoundRectangle2D.Float(size / 2.0f - 1, 13, 2, 14, 2, 2));
        graphics.fill(new RoundRectangle2D.Float(size / 2.0f + 3, 10, 2, 17, 2, 2));
        graphics.setColor(new Color((accent & 0x00FFFFFF) | 0xFF000000, true));
        graphics.fill(new java.awt.geom.Ellipse2D.Float(size / 2.0f - 2.5f, 22, 6, 6));
        graphics.dispose();
        return downsample(highRes, size * density, size * density);
    }

    private static BufferedImage createMediaPanelImage(
            int width, int height, int color, int density) {
        BufferedImage highRes = new BufferedImage(width * density * SUPERSAMPLE,
                height * density * SUPERSAMPLE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = highRes.createGraphics();
        configureAntialiasing(graphics);
        graphics.scale(density * SUPERSAMPLE, density * SUPERSAMPLE);
        graphics.setColor(new Color(color, true));
        graphics.fill(new RoundRectangle2D.Float(0, 0, width, height, height, height));
        graphics.setColor(new Color(0x20FFFFFF, true));
        graphics.draw(new RoundRectangle2D.Float(0.5f, 0.5f, width - 1, height - 1,
                height - 1, height - 1));
        graphics.dispose();
        return downsample(highRes, width * density, height * density);
    }

    private static BufferedImage createMediaTextImage(
            String text, int color, int width, int height, int density) {
        BufferedImage highRes = new BufferedImage(width * density * SUPERSAMPLE,
                height * density * SUPERSAMPLE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = highRes.createGraphics();
        configureAntialiasing(graphics);
        graphics.scale(density * SUPERSAMPLE, density * SUPERSAMPLE);
        graphics.setFont(MEDIA_FONT.deriveFont(MEDIA_FONT.getSize2D() / SUPERSAMPLE));
        graphics.setColor(new Color(color, true));
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(text, 1, 1 + metrics.getAscent());
        graphics.dispose();
        return downsample(highRes, width * density, height * density);
    }

    private static BufferedImage createTextImage(String text, int color, int width, int height) {
        BufferedImage highRes = new BufferedImage(width * SUPERSAMPLE, height * SUPERSAMPLE,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = highRes.createGraphics();
        configureAntialiasing(graphics);
        graphics.setFont(FONT);
        graphics.setColor(new Color(color, true));
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(text, SUPERSAMPLE, SUPERSAMPLE + metrics.getAscent());
        graphics.dispose();
        return highRes;
    }

    private static void configureAntialiasing(Graphics2D graphics) {
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static BufferedImage downsample(BufferedImage source, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        source.flush();
        return result;
    }

    private static NativeImage toNativeImage(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("PNG encoder is unavailable");
            }
            return NativeImage.read(output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create an antialiased HUD texture", exception);
        } finally {
            image.flush();
        }
    }

    private static void blit(GuiGraphics graphics, Texture texture, int x, int y) {
        blit(graphics, texture, x, y, 0xFFFFFFFF);
    }

    private static void blit(GuiGraphics graphics, Texture texture, int x, int y, int tint) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id, x, y,
                0, 0, texture.width, texture.height,
                texture.textureWidth, texture.textureHeight,
                texture.textureWidth, texture.textureHeight, tint);
    }

    @FunctionalInterface
    private interface ImageFactory {
        BufferedImage create();
    }

    private record Texture(Identifier id, int width, int height, int textureWidth, int textureHeight) {
    }
}
