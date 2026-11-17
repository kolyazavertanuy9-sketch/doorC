package dev.crystalutility.labels;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.crystalutility.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BlockLabelManager {
    private BlockLabelManager() {
    }

    public static void addOrUpdateAt(BlockPos pos, String text) {
        ModConfig config = ModConfig.get();
        if (pos == null) return;
        String value = text == null ? "Marker" : text.trim();
        if (value.isEmpty()) value = "Marker";

        for (BlockLabel label : config.blockLabels) {
            if (label.matches(pos)) {
                label.text = value;
                label.color = config.blockLabelColor;
                ModConfig.save();
                return;
            }
        }

        config.blockLabels.add(BlockLabel.create(pos, value, config.blockLabelColor));
        ModConfig.save();
    }

    public static void removeAt(BlockPos pos) {
        if (pos == null) return;
        ModConfig config = ModConfig.get();
        config.blockLabels.removeIf(label -> label.matches(pos));
        ModConfig.save();
    }

    public static void renderWorld(WorldRenderContext context) {
        ModConfig config = ModConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (!config.blockLabelsEnabled || !config.blockLabelHighlightEnabled || minecraft.level == null
                || context.consumers() == null || config.blockLabels.isEmpty()) {
            return;
        }

        Vec3 camera = context.worldState().cameraRenderState.pos;
        if (camera == null) return;

        PoseStack matrices = context.matrices();
        VertexConsumer lines = context.consumers().getBuffer(RenderTypes.LINES_TRANSLUCENT);
        double maxDistanceSquared = (double) config.blockLabelRange * config.blockLabelRange;
        Vec3 offset = new Vec3(-camera.x, -camera.y, -camera.z);

        for (BlockLabel label : config.blockLabels) {
            BlockPos pos = label.toBlockPos();
            if (Vec3.atCenterOf(pos).distanceToSqr(camera) > maxDistanceSquared) continue;
            int color = ModConfig.parseColor(label.color, ModConfig.parseColor(config.blockLabelColor, 0xFFFFC857));
            drawBox(matrices, lines, new AABB(pos).move(offset), color, 1.8f);
        }
    }

    public static void renderHud(GuiGraphics graphics) {
        ModConfig config = ModConfig.get();
        if (!config.blockLabelsEnabled || config.blockLabels.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        int x = 12;
        int y = 12;
        for (BlockLabel label : config.blockLabels) {
            int color = ModConfig.parseColor(label.color, ModConfig.parseColor(config.blockLabelColor, 0xFFFFC857));
            graphics.drawString(minecraft.font, label.text + " @ " + label.x + "," + label.y + "," + label.z,
                    x, y, color, false);
            y += 12;
        }
    }

    private static void drawBox(PoseStack matrices, VertexConsumer consumer, AABB bounds, int color, float lineWidth) {
        Vec3 a = new Vec3(bounds.minX, bounds.minY, bounds.minZ);
        Vec3 b = new Vec3(bounds.maxX, bounds.minY, bounds.minZ);
        Vec3 c = new Vec3(bounds.maxX, bounds.minY, bounds.maxZ);
        Vec3 d = new Vec3(bounds.minX, bounds.minY, bounds.maxZ);
        Vec3 e = new Vec3(bounds.minX, bounds.maxY, bounds.minZ);
        Vec3 f = new Vec3(bounds.maxX, bounds.maxY, bounds.minZ);
        Vec3 g = new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ);
        Vec3 h = new Vec3(bounds.minX, bounds.maxY, bounds.maxZ);

        line(matrices, consumer, a, b, color, lineWidth);
        line(matrices, consumer, b, c, color, lineWidth);
        line(matrices, consumer, c, d, color, lineWidth);
        line(matrices, consumer, d, a, color, lineWidth);
        line(matrices, consumer, e, f, color, lineWidth);
        line(matrices, consumer, f, g, color, lineWidth);
        line(matrices, consumer, g, h, color, lineWidth);
        line(matrices, consumer, h, e, color, lineWidth);
        line(matrices, consumer, a, e, color, lineWidth);
        line(matrices, consumer, b, f, color, lineWidth);
        line(matrices, consumer, c, g, color, lineWidth);
        line(matrices, consumer, d, h, color, lineWidth);
    }

    private static void line(PoseStack matrices, VertexConsumer consumer, Vec3 from, Vec3 to,
                             int color, float lineWidth) {
        PoseStack.Pose pose = matrices.last();
        consumer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(red(color), green(color), blue(color), alpha(color))
                .setNormal(pose, 0.0f, 1.0f, 0.0f)
                .setLineWidth(lineWidth);
        consumer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(red(color), green(color), blue(color), alpha(color))
                .setNormal(pose, 0.0f, 1.0f, 0.0f)
                .setLineWidth(lineWidth);
    }

    private static float red(int color) { return ((color >>> 16) & 0xFF) / 255.0f; }
    private static float green(int color) { return ((color >>> 8) & 0xFF) / 255.0f; }
    private static float blue(int color) { return (color & 0xFF) / 255.0f; }
    private static float alpha(int color) { return ((color >>> 24) & 0xFF) / 255.0f; }
}
