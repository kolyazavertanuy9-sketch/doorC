package dev.crystalutility.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

final class MediaArtwork {
    private static final Identifier TEXTURE_ID =
            Identifier.fromNamespaceAndPath("crystalutility", "dynamic/media_artwork");
    private static final int MAX_ARTWORK_BYTES = 16 * 1024 * 1024;
    private static final int MAX_SOURCE_DIMENSION = 4096;
    private static final long MAX_SOURCE_PIXELS = 16_777_216L;
    private static final int ARTWORK_SIZE = 40;
    private static final int ARTWORK_SUPERSAMPLE = 4;
    private static final long RETRY_DELAY_MILLIS = 5_000L;
    private static String loadedData = "";
    private static int loadedDensity;
    private static boolean loadFailed;
    private static long retryAfterMillis;

    private MediaArtwork() {}

    static Identifier texture(String artworkBase64, int density) {
        if (artworkBase64 == null || artworkBase64.isBlank()) {
            release();
            return null;
        }
        if (artworkBase64.equals(loadedData) && density == loadedDensity) {
            if (!loadFailed) return TEXTURE_ID;
            if (System.currentTimeMillis() < retryAfterMillis) return null;
        }

        if (!artworkBase64.equals(loadedData) || density != loadedDensity) {
            release();
            loadedData = artworkBase64;
            loadedDensity = density;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(artworkBase64);
            if (bytes.length == 0 || bytes.length > MAX_ARTWORK_BYTES) {
                throw new IllegalArgumentException("Media artwork exceeds the 16 MiB limit.");
            }
            int[] dimensions = imageDimensions(bytes);
            int sourceWidth = dimensions[0];
            int sourceHeight = dimensions[1];
            if (sourceWidth > MAX_SOURCE_DIMENSION || sourceHeight > MAX_SOURCE_DIMENSION
                    || (long) sourceWidth * sourceHeight > MAX_SOURCE_PIXELS) {
                throw new IllegalArgumentException("Media artwork exceeds supported image dimensions.");
            }
            NativeImage image = createRoundedArtwork(bytes, density, sourceWidth, sourceHeight);
            DynamicTexture texture = new SmoothDynamicTexture(() -> "wvDoorClient media artwork", image);
            Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
            loadFailed = false;
            retryAfterMillis = 0L;
            return TEXTURE_ID;
        } catch (Exception exception) {
            loadFailed = true;
            retryAfterMillis = System.currentTimeMillis() + RETRY_DELAY_MILLIS;
            System.err.println("[wvDoorClient] Could not load media artwork: " + exception.getMessage());
            return null;
        }
    }

    private static void release() {
        if (loadedData.isEmpty()) return;
        Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
        loadedData = "";
        loadedDensity = 0;
        loadFailed = false;
        retryAfterMillis = 0L;
    }

    private static NativeImage createRoundedArtwork(
            byte[] sourceBytes, int density, int sourceWidth, int sourceHeight) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(sourceBytes));
        if (source == null) throw new IOException("Unsupported media artwork image.");

        int side = Math.min(sourceWidth, sourceHeight);
        int cropX = (sourceWidth - side) / 2;
        int cropY = (sourceHeight - side) / 2;
        int targetSize = ARTWORK_SIZE * density;
        int highResolutionSize = targetSize * ARTWORK_SUPERSAMPLE;
        BufferedImage highResolution = new BufferedImage(highResolutionSize, highResolutionSize,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = highResolution.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setClip(new RoundRectangle2D.Float(0, 0, highResolutionSize, highResolutionSize,
                highResolutionSize * 0.44f, highResolutionSize * 0.44f));
        graphics.drawImage(source, 0, 0, highResolutionSize, highResolutionSize,
                cropX, cropY, cropX + side, cropY + side, null);
        graphics.dispose();
        source.flush();

        BufferedImage result = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D downsampler = result.createGraphics();
        downsampler.setComposite(AlphaComposite.Src);
        downsampler.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        downsampler.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        downsampler.drawImage(highResolution, 0, 0, targetSize, targetSize, null);
        downsampler.dispose();
        highResolution.flush();

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(result, "png", output)) {
                throw new IOException("PNG encoder is unavailable.");
            }
            return NativeImage.read(output.toByteArray());
        } finally {
            result.flush();
        }
    }

    private static int[] imageDimensions(byte[] imageBytes) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            if (input == null) throw new IOException("Could not read media artwork image.");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported media artwork image.");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }
}
