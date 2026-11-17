package dev.crystalutility.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.crystalutility.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class HitboxRenderer {
    private HitboxRenderer() {}

    public static void render(WorldRenderContext context) {
        ModConfig config = ModConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || context.consumers() == null) return;

        Vec3 camera = context.worldState().cameraRenderState.pos;
        if (camera == null) return;
        Vec3 cameraOffset = new Vec3(-camera.x, -camera.y, -camera.z);
        double maxDistanceSquared = (double) config.hitboxRange * config.hitboxRange;
        PoseStack matrices = context.matrices();
        VertexConsumer lines = context.consumers().getBuffer(RenderTypes.LINES_TRANSLUCENT);
        Entity target = minecraft.crosshairPickEntity;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            boolean targeted = config.targetHitboxHighlightEnabled && entity == target && entity instanceof LivingEntity;
            if ((!config.hitboxesEnabled && !targeted) || !entity.isAlive() || entity == minecraft.getCameraEntity()
                    || entity.distanceToSqr(camera) > maxDistanceSquared) {
                continue;
            }
            int color = targeted
                    ? ModConfig.parseColor(config.targetHitboxColor, 0xFFFF566B)
                    : ModConfig.parseColor(config.hitboxColor, 0xCC55AAFF);
            box(matrices, lines, entity.getBoundingBox().move(cameraOffset), color, config.lineThickness);

            if (config.hitboxesEnabled && config.renderEyeLines) {
                int eye = ModConfig.parseColor(config.eyeColor, 0xFFFFCC33);
                Vec3 eyePoint = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ()).add(cameraOffset);
                line(matrices, lines, eyePoint.add(-0.15, 0, 0), eyePoint.add(0.15, 0, 0),
                        eye, config.lineThickness);
            }
            if (config.hitboxesEnabled && config.renderFacingVectors) {
                Vec3 eyePoint = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ()).add(cameraOffset);
                line(matrices, lines, eyePoint, eyePoint.add(entity.getLookAngle().normalize().scale(0.65)),
                        ModConfig.parseColor(config.facingColor, 0xFF55FF88), config.lineThickness);
            }
        }
    }

    private static void box(PoseStack matrices, VertexConsumer consumer, AABB bounds, int color, float lineWidth) {
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
        Vec3 direction = to.subtract(from).normalize();
        Vec3 normal = Math.abs(direction.y) > 0.98 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        PoseStack.Pose pose = matrices.last();
        vertex(consumer, pose, from, color, normal, lineWidth);
        vertex(consumer, pose, to, color, normal, lineWidth);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 point,
                               int color, Vec3 normal, float lineWidth) {
        consumer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(red(color), green(color), blue(color), alpha(color))
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z)
                .setLineWidth(lineWidth);
    }

    private static float red(int color) { return ((color >>> 16) & 0xFF) / 255.0f; }
    private static float green(int color) { return ((color >>> 8) & 0xFF) / 255.0f; }
    private static float blue(int color) { return (color & 0xFF) / 255.0f; }
    private static float alpha(int color) { return ((color >>> 24) & 0xFF) / 255.0f; }
}
