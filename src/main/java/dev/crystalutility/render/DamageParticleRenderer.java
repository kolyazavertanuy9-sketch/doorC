package dev.crystalutility.render;

import dev.crystalutility.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class DamageParticleRenderer {
    private static final long LIFETIME_NANOS = 1_050_000_000L;
    private static final Map<UUID, HealthSample> HEALTH = new HashMap<>();
    private static final List<DamageParticle> PARTICLES = new ArrayList<>();
    private static Object trackedLevel;
    private static long tick;

    private DamageParticleRenderer() {}

    public static void tick(Minecraft client) {
        if (!ModConfig.get().damageParticlesEnabled || client.level == null) {
            HEALTH.clear();
            PARTICLES.clear();
            trackedLevel = null;
            return;
        }
        if (trackedLevel != client.level) {
            HEALTH.clear();
            PARTICLES.clear();
            trackedLevel = client.level;
        }

        tick++;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            UUID id = living.getUUID();
            float health = living.getHealth();
            HealthSample previous = HEALTH.put(id, new HealthSample(health, tick));
            if (previous == null || health >= previous.health - 0.01f) continue;

            float damage = previous.health - health;
            if (damage > 0.05f && damage <= 40.0f) {
                String amount = String.format(Locale.ROOT, "%.1f", damage);
                if (amount.endsWith(".0")) amount = amount.substring(0, amount.length() - 2);
                Vec3 anchor = new Vec3(living.getX(), living.getY() + living.getBbHeight() * 0.78,
                        living.getZ());
                String text = "-" + amount;
                PARTICLES.add(new DamageParticle(text, SmoothHudRenderer.textWidth(text) + 10,
                        anchor, System.nanoTime(), PARTICLES.size() * 17 % 31 - 15));
                if (PARTICLES.size() > 48) PARTICLES.remove(0);
            }
        }

        HEALTH.entrySet().removeIf(entry -> tick - entry.getValue().lastSeenTick > 100);
        long now = System.nanoTime();
        PARTICLES.removeIf(particle -> now - particle.createdAt >= LIFETIME_NANOS);
    }

    public static void render(GuiGraphics graphics) {
        ModConfig config = ModConfig.get();
        Minecraft client = Minecraft.getInstance();
        if (!config.damageParticlesEnabled || client.level == null || client.player == null
                || client.options.hideGui || client.screen != null) {
            return;
        }

        long now = System.nanoTime();
        Iterator<DamageParticle> iterator = PARTICLES.iterator();
        while (iterator.hasNext()) {
            DamageParticle particle = iterator.next();
            float progress = Math.min(1.0f, (float) (now - particle.createdAt) / LIFETIME_NANOS);
            Vec3 cameraPosition = client.gameRenderer.getMainCamera().position();
            if (particle.position.distanceToSqr(cameraPosition) > 40.0 * 40.0) continue;

            Vec3 projected = client.gameRenderer.projectPointToScreen(
                    particle.position.add(0, progress * 0.72, 0));
            if (projected.z < -1.0 || projected.z > 1.0
                    || projected.x < -1.1 || projected.x > 1.1
                    || projected.y < -1.1 || projected.y > 1.1) {
                continue;
            }

            float fade = 1.0f - Math.max(0.0f, (progress - 0.62f) / 0.38f);
            int color = ModConfig.parseColor(config.damageParticleColor, 0xFFFF5363);
            color = color & 0x00FFFFFF | Math.round(((color >>> 24) & 0xFF) * fade) << 24;
            int x = Math.round((float) ((projected.x + 1.0) * 0.5 * graphics.guiWidth()))
                    + particle.horizontalOffset;
            int y = Math.round((float) ((1.0 - projected.y) * 0.5 * graphics.guiHeight())
                    - progress * 8.0f);
            int shadow = Math.round(((color >>> 24) & 0xFF) * 0.72f) << 24;
            SmoothHudRenderer.drawCenteredTextTinted(graphics, particle.text,
                    x - particle.textWidth / 2 + 1, y + 1, particle.textWidth, 18, shadow);
            SmoothHudRenderer.drawCenteredTextTinted(graphics, particle.text,
                    x - particle.textWidth / 2, y, particle.textWidth, 18, color);
        }
    }

    private record HealthSample(float health, long lastSeenTick) {}
    private record DamageParticle(
            String text, int textWidth, Vec3 position, long createdAt, int horizontalOffset) {}
}
