package dev.crystalutility.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.crystalutility.CrystalUtilityClient;
import dev.crystalutility.config.ModConfig;
import dev.crystalutility.text.UiText;
import dev.wvlib.api.ThemeColors;
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
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

public final class LoadingScreenRenderer {
    private static final String TEXTURE_PREFIX = "dynamic/loading_screen_";
    private static final int CARD_WIDTH = 420;
    private static final int CARD_HEIGHT = 190;
    private static final int BAR_WIDTH = 300;
    private static final int BAR_HEIGHT = 5;
    private static final int SUPERSAMPLE = 4;
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();
    private static long nextTextureId;
    private static int cachedWidth = -1;
    private static int cachedHeight = -1;
    private static int cachedDensity = -1;
    private static int cachedAccent;
    private static int cachedBackground;
    private static String cachedSubtitle;
    private static Identifier background;
    private static int backgroundTextureWidth;
    private static int backgroundTextureHeight;
    private static Identifier card;
    private static Identifier crystal;
    private static TextTexture wordmark;
    private static TextTexture subtitle;
    private static TextTexture percentage;
    private static Identifier progressFill;
    private static int progressFillWidth = -1;

    private LoadingScreenRenderer() {}

    public static void render(GuiGraphics graphics, float progress, float alpha, long timeMillis) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int density = Math.max(1, Math.min(3,
                (int) Math.ceil(Minecraft.getInstance().getWindow().getGuiScale())));
        ModConfig config = ModConfig.get();
        int accent = ThemeColors.accent(config.uiAccentColor);
        int backgroundColor = ModConfig.parseColor(config.uiBackgroundColor, 0xFF090B12);
        ensureTextures(width, height, density, accent, backgroundColor);

        int tint = ARGB.white(alpha);
        draw(graphics, background, 0, 0, width, height,
                backgroundTextureWidth, backgroundTextureHeight, tint);

        int cardWidth = Math.min(CARD_WIDTH, Math.max(260, width - 32));
        int cardX = (width - cardWidth) / 2;
        int cardY = (height - CARD_HEIGHT) / 2;
        draw(graphics, card, cardX, cardY, cardWidth, CARD_HEIGHT,
                CARD_WIDTH * density, CARD_HEIGHT * density, tint);

        float pulse = 1.0f + 0.025f * (float) Math.sin(timeMillis / 480.0);
        int crystalSize = 62;
        float centerX = width / 2.0f;
        float centerY = cardY + 45;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(pulse, pulse);
        graphics.pose().translate(-centerX, -centerY);
        draw(graphics, crystal, Math.round(centerX - crystalSize / 2.0f),
                Math.round(centerY - crystalSize / 2.0f), crystalSize, crystalSize,
                crystalSize * density, crystalSize * density, tint);
        graphics.pose().popMatrix();

        draw(graphics, wordmark.id, (width - wordmark.width) / 2, cardY + 83,
                wordmark.width, wordmark.height, wordmark.width * density,
                wordmark.height * density, tint);
        draw(graphics, subtitle.id, (width - subtitle.width) / 2, cardY + 117,
                subtitle.width, subtitle.height, subtitle.width * density,
                subtitle.height * density, withAlpha(alpha, 0.72f));

        int barWidth = Math.min(BAR_WIDTH, Math.max(1, width - 64));
        int barX = (width - barWidth) / 2;
        int barY = cardY + 151;
        graphics.fill(barX, barY, barX + barWidth, barY + BAR_HEIGHT,
                Math.round(alpha * 0.20f * 255) << 24 | 0x00FFFFFF);
        int progressStep = Math.round(progress * 100.0f);
        int filled = Math.max(0, Math.min(barWidth,
                Math.round(barWidth * progressStep / 100.0f)));
        updateProgressFill(filled, density, accent);
        if (progressFill != null && filled > 0) {
            draw(graphics, progressFill, barX, barY, filled, BAR_HEIGHT,
                    filled * density, BAR_HEIGHT * density, tint);
        }

        String percentageText = Math.round(progress * 100) + "%";
        updatePercentage(percentageText, density);
        draw(graphics, percentage.id, (width - percentage.width) / 2, barY + 15,
                percentage.width, percentage.height, percentage.width * density,
                percentage.height * density, withAlpha(alpha, 0.78f));
    }

    private static void ensureTextures(int width, int height, int density, int accent, int backgroundColor) {
        String localizedSubtitle = UiText.tr("Preparing your world");
        if (width == cachedWidth && height == cachedHeight && density == cachedDensity
                && accent == cachedAccent && backgroundColor == cachedBackground
                && localizedSubtitle.equals(cachedSubtitle)) {
            return;
        }
        releaseTextures();
        cachedWidth = width;
        cachedHeight = height;
        cachedDensity = density;
        cachedAccent = accent;
        cachedBackground = backgroundColor;
        cachedSubtitle = localizedSubtitle;
        int textureWidth = Math.min(1920, width * density);
        int textureHeight = Math.max(1,
                Math.round((float) height * textureWidth / Math.max(1, width)));
        backgroundTextureWidth = textureWidth;
        backgroundTextureHeight = textureHeight;
        background = register("background",
                createBackground(width, height, textureWidth, textureHeight, accent, backgroundColor));
        card = register("card", createCard(accent, backgroundColor, density));
        crystal = register("crystal", createCrystal(accent, density));
        wordmark = createText("wvDoorClient", 0xFFF4F7FF, 27, Font.BOLD, density);
        subtitle = createText(localizedSubtitle, 0xFFB7C2D3, 10, Font.PLAIN, density);
        percentage = null;
        progressFill = null;
        progressFillWidth = -1;
    }

    private static BufferedImage createBackground(
            int width, int height, int textureWidth, int textureHeight, int accent, int backgroundColor) {
        BufferedImage image = new BufferedImage(textureWidth, textureHeight,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);
        graphics.scale((float) textureWidth / width, (float) textureHeight / height);
        graphics.setPaint(new GradientPaint(0, 0, new Color(backgroundColor, true),
                width, height, new Color(0xFF101725, true)));
        graphics.fillRect(0, 0, width, height);

        int glow = (accent & 0x00FFFFFF) | 0x3A000000;
        graphics.setPaint(new java.awt.RadialGradientPaint(
                new java.awt.geom.Point2D.Float(width * 0.5f, height * 0.39f),
                Math.max(width, height) * 0.62f,
                new float[]{0.0f, 0.48f, 1.0f},
                new Color[]{new Color(glow, true), new Color((accent & 0x00FFFFFF) | 0x14000000, true),
                        new Color(0x00000000, true)}));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    private static BufferedImage createCard(int accent, int backgroundColor, int density) {
        BufferedImage image = new BufferedImage(CARD_WIDTH * density, CARD_HEIGHT * density,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);
        graphics.scale(density, density);
        graphics.setColor(new Color((backgroundColor & 0x00FFFFFF) | 0xE9000000, true));
        graphics.fill(new RoundRectangle2D.Float(0.5f, 0.5f, CARD_WIDTH - 1, CARD_HEIGHT - 1, 26, 26));
        graphics.setColor(new Color(0x20FFFFFF, true));
        graphics.draw(new RoundRectangle2D.Float(1, 1, CARD_WIDTH - 2, CARD_HEIGHT - 2, 25, 25));
        graphics.setPaint(new GradientPaint(100, 1, new Color((accent & 0x00FFFFFF) | 0x00000000, true),
                CARD_WIDTH - 100, 1, new Color((accent & 0x00FFFFFF) | 0x75000000, true)));
        graphics.fill(new RoundRectangle2D.Float(54, 1, CARD_WIDTH - 108, 1.5f, 2, 2));
        graphics.dispose();
        return image;
    }

    private static BufferedImage createCrystal(int accent, int density) {
        BufferedImage image = new BufferedImage(62 * density, 62 * density,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);
        graphics.scale(density, density);
        graphics.setPaint(new java.awt.RadialGradientPaint(
                new java.awt.geom.Point2D.Float(31, 31), 30,
                new float[]{0.0f, 0.58f, 1.0f},
                new Color[]{new Color((accent & 0x00FFFFFF) | 0x48000000, true),
                        new Color((accent & 0x00FFFFFF) | 0x18000000, true),
                        new Color(0x00000000, true)}));
        graphics.fill(new java.awt.geom.Ellipse2D.Float(1, 1, 60, 60));

        Path2D gem = new Path2D.Float();
        gem.moveTo(31, 7);
        gem.lineTo(49, 25);
        gem.lineTo(31, 53);
        gem.lineTo(13, 25);
        gem.closePath();
        graphics.setPaint(new GradientPaint(16, 14, new Color(0xFFF7FBFF, true),
                46, 48, new Color((accent & 0x00FFFFFF) | 0xFF000000, true)));
        graphics.fill(gem);
        graphics.setColor(new Color(0xDFFFFFFF, true));
        graphics.setStroke(new java.awt.BasicStroke(0.8f,
                java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        graphics.draw(gem);

        Path2D leftFacet = new Path2D.Float();
        leftFacet.moveTo(31, 7);
        leftFacet.lineTo(31, 53);
        leftFacet.lineTo(13, 25);
        leftFacet.closePath();
        graphics.setColor(new Color((accent & 0x00FFFFFF) | 0x66000000, true));
        graphics.fill(leftFacet);
        Path2D rightFacet = new Path2D.Float();
        rightFacet.moveTo(31, 7);
        rightFacet.lineTo(49, 25);
        rightFacet.lineTo(31, 26);
        rightFacet.closePath();
        graphics.setColor(new Color(0x88FFFFFF, true));
        graphics.fill(rightFacet);
        graphics.dispose();
        return image;
    }

    private static TextTexture createText(String value, int color, int size, int style, int density) {
        Font font = new Font(Font.SANS_SERIF, style, size);
        BufferedImage measureImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measureGraphics = measureImage.createGraphics();
        measureGraphics.setFont(font);
        FontMetrics metrics = measureGraphics.getFontMetrics();
        int width = Math.max(1, metrics.stringWidth(value) + 4);
        int height = Math.max(1, metrics.getHeight() + 4);
        measureGraphics.dispose();

        BufferedImage highRes = new BufferedImage(width * density * SUPERSAMPLE,
                height * density * SUPERSAMPLE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = highRes.createGraphics();
        configure(graphics);
        graphics.scale(density * SUPERSAMPLE, density * SUPERSAMPLE);
        graphics.setFont(font);
        graphics.setColor(new Color(color, true));
        graphics.drawString(value, 2, 2 + graphics.getFontMetrics().getAscent());
        graphics.dispose();

        BufferedImage result = downsample(highRes, width * density, height * density);
        return new TextTexture(register("text", result), width, height);
    }

    private static void updatePercentage(String value, int density) {
        if (percentage != null && percentage.value.equals(value)) return;
        if (percentage != null) release(percentage.id);
        percentage = createText(value, 0xFFDCE5F2, 10, Font.PLAIN, density);
        percentage.value = value;
    }

    private static void updateProgressFill(int width, int density, int accent) {
        if (width == progressFillWidth) return;
        if (progressFill != null) release(progressFill);
        progressFill = null;
        progressFillWidth = width;
        if (width <= 0) return;

        BufferedImage image = new BufferedImage(width * density, BAR_HEIGHT * density,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);
        graphics.scale(density, density);
        graphics.setPaint(new GradientPaint(0, 0,
                new Color((accent & 0x00FFFFFF) | 0xFF000000, true),
                width, BAR_HEIGHT, new Color((accent & 0x00FFFFFF) | 0xB8000000, true)));
        graphics.fill(new RoundRectangle2D.Float(0, 0, width, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT));
        graphics.dispose();
        progressFill = register("progress", image);
    }

    private static Identifier register(String name, BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("PNG encoder is unavailable.");
            }
            NativeImage nativeImage = NativeImage.read(output.toByteArray());
            String textureName = name + "_" + nextTextureId++;
            Identifier id = Identifier.fromNamespaceAndPath(CrystalUtilityClient.MOD_ID,
                    TEXTURE_PREFIX + textureName);
            DynamicTexture texture = new SmoothDynamicTexture(
                    () -> "wvDoorClient loading screen", nativeImage);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            TEXTURES.put(textureName, id);
            return id;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create loading screen texture.", exception);
        } finally {
            image.flush();
        }
    }

    private static void releaseTextures() {
        for (Identifier id : TEXTURES.values()) {
            Minecraft.getInstance().getTextureManager().release(id);
        }
        TEXTURES.clear();
        background = null;
        card = null;
        crystal = null;
        wordmark = null;
        subtitle = null;
        percentage = null;
        progressFill = null;
        progressFillWidth = -1;
    }

    private static void release(Identifier id) {
        Minecraft.getInstance().getTextureManager().release(id);
        TEXTURES.values().removeIf(id::equals);
    }

    private static void draw(
            GuiGraphics graphics, Identifier id, int x, int y, int width, int height,
            int textureWidth, int textureHeight, int tint) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0,
                width, height, textureWidth, textureHeight, textureWidth, textureHeight, tint);
    }

    private static int withAlpha(float alpha, float multiplier) {
        return ARGB.white(Math.max(0.0f, Math.min(1.0f, alpha * multiplier)));
    }

    private static void configure(Graphics2D graphics) {
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
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

    private static final class TextTexture {
        private final Identifier id;
        private final int width;
        private final int height;
        private String value = "";

        private TextTexture(Identifier id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }
    }
}
