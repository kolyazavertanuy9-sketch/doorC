package dev.crystalutility.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.function.Supplier;

public final class SmoothDynamicTexture extends DynamicTexture {
    public SmoothDynamicTexture(Supplier<String> name, NativeImage image) {
        super(name, image);
        sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }
}
