package dev.crystalutility;

import dev.crystalutility.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class CrystalVisibility {
    private static final Map<UUID, HiddenCrystal> HIDDEN_UNTIL_TICK = new HashMap<>();
    private static long clientTick;
    private static ClientLevel lastLevel;

    private CrystalVisibility() {}

    public static void hideAfterAttack(Entity entity) {
        if (ModConfig.get().crystalOptimizationEnabled
                && ModConfig.get().hideAttackedCrystalsImmediately
                && entity instanceof EndCrystal) {
            HIDDEN_UNTIL_TICK.put(entity.getUUID(), new HiddenCrystal(entity.position(), clientTick + 12));
        }
    }

    public static boolean shouldHideAt(double x, double y, double z) {
        for (HiddenCrystal hidden : HIDDEN_UNTIL_TICK.values()) {
            if (hidden.expiresAtTick >= clientTick
                    && hidden.position.distanceToSqr(x, y, z) < 0.01) {
                return true;
            }
        }
        return false;
    }

    public static void tick(Minecraft client) {
        clientTick++;
        if (client.level != lastLevel) {
            HIDDEN_UNTIL_TICK.clear();
            lastLevel = client.level;
        }
        Iterator<Map.Entry<UUID, HiddenCrystal>> iterator = HIDDEN_UNTIL_TICK.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, HiddenCrystal> entry = iterator.next();
            if (entry.getValue().expiresAtTick < clientTick) {
                iterator.remove();
            }
        }
    }

    private record HiddenCrystal(Vec3 position, long expiresAtTick) {}
}
