package dev.crystalutility.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.crystalutility.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BlockHighlightRenderer {
    private BlockHighlightRenderer() {}

    public static void render(WorldRenderContext context) {
        ModConfig config = ModConfig.get();
        Minecraft client = Minecraft.getInstance();
        if (!config.blockHighlightEnabled || client.level == null || context.consumers() == null) return;

        BlockOutlineRenderState outline = context.worldState().blockOutlineRenderState;
        if (outline == null || outline.shape() == null || outline.shape().isEmpty()) return;
        Vec3 camera = context.worldState().cameraRenderState.pos;
        if (camera == null) return;

        int color = ModConfig.parseColor(config.blockHighlightColor, 0xFF3478F6);
        Vec3 offset = new Vec3(outline.pos().getX() - camera.x,
                outline.pos().getY() - camera.y, outline.pos().getZ() - camera.z);
        PoseStack matrices = context.matrices();
        VoxelShape shape = outline.shape();

        VertexConsumer surfaces = context.consumers().getBuffer(RenderTypes.debugQuads());
        int fillColor = color & 0x00FFFFFF | 0x48000000;
        for (AABB box : shape.toAabbs()) {
            drawBoxFaces(matrices, surfaces, box.move(offset), fillColor);
        }

        VertexConsumer lines = context.consumers().getBuffer(RenderTypes.LINES_TRANSLUCENT);
        PoseStack.Pose pose = matrices.last();
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            Vec3 from = new Vec3(x1 + offset.x, y1 + offset.y, z1 + offset.z);
            Vec3 to = new Vec3(x2 + offset.x, y2 + offset.y, z2 + offset.z);
            Vec3 direction = to.subtract(from).normalize();
            Vec3 normal = Math.abs(direction.y) > 0.98 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            vertex(lines, pose, from, color, normal, 2.2f);
            vertex(lines, pose, to, color, normal, 2.2f);
        });
    }

    private static void drawBoxFaces(PoseStack matrices, VertexConsumer consumer, AABB box, int color) {
        PoseStack.Pose pose = matrices.last();
        face(consumer, pose, color, new Vec3(0, 0, -1),
                new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.minZ), new Vec3(box.minX, box.maxY, box.minZ));
        face(consumer, pose, color, new Vec3(0, 0, 1),
                new Vec3(box.maxX, box.minY, box.maxZ), new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ), new Vec3(box.maxX, box.maxY, box.maxZ));
        face(consumer, pose, color, new Vec3(-1, 0, 0),
                new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.minX, box.maxY, box.maxZ));
        face(consumer, pose, color, new Vec3(1, 0, 0),
                new Vec3(box.maxX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.maxY, box.maxZ), new Vec3(box.maxX, box.maxY, box.minZ));
        face(consumer, pose, color, new Vec3(0, -1, 0),
                new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.minY, box.minZ), new Vec3(box.minX, box.minY, box.minZ));
        face(consumer, pose, color, new Vec3(0, 1, 0),
                new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ), new Vec3(box.minX, box.maxY, box.maxZ));
    }

    private static void face(VertexConsumer consumer, PoseStack.Pose pose, int color, Vec3 normal,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        vertex(consumer, pose, a, color, normal, 0);
        vertex(consumer, pose, b, color, normal, 0);
        vertex(consumer, pose, c, color, normal, 0);
        vertex(consumer, pose, d, color, normal, 0);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 point,
                               int color, Vec3 normal, float lineWidth) {
        VertexConsumer rendered = consumer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor((color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        if (lineWidth > 0) {
            rendered.setLineWidth(lineWidth);
        }
    }
}
