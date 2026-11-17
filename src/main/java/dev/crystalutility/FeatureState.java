package dev.crystalutility;

import dev.crystalutility.config.ModConfig;
import dev.crystalutility.mixin.OptionsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

public final class FeatureState {
    private static boolean sprintToggled;
    private static boolean sneakToggled;
    private static boolean freeLook;
    private static float freeLookYaw;
    private static float freeLookPitch;
    private static long comboUntil;
    private static int comboCount;
    private static int comboEntityId = -1;

    private FeatureState() {}

    public static void toggleSprint() {
        sprintToggled = !sprintToggled;
    }

    public static void toggleSneak() {
        sneakToggled = !sneakToggled;
    }

    public static boolean sprintToggled() {
        return sprintToggled;
    }

    public static boolean sneakToggled() {
        return sneakToggled;
    }

    public static boolean freeLook() {
        return freeLook && ModConfig.get().freeLookEnabled;
    }

    public static void toggleFreeLook(Minecraft client) {
        if (freeLook) {
            freeLook = false;
            return;
        }
        if (client.player == null || client.screen != null) return;
        freeLook = true;
        freeLookYaw = client.player.getYRot();
        freeLookPitch = client.player.getXRot();
    }

    public static void disableFreeLook() {
        freeLook = false;
    }

    public static void rotateFreeLook(double deltaX, double deltaY) {
        Minecraft client = Minecraft.getInstance();
        double sensitivity = client.options.sensitivity().get() * 0.6 + 0.2;
        double scale = sensitivity * sensitivity * sensitivity * 8.0;
        freeLookYaw += (float) (deltaX * scale);
        freeLookPitch += (float) ((((OptionsAccessor) client.options).crystalutility$getInvertYMouse().get()
                ? deltaY : -deltaY) * scale);
        freeLookPitch = Math.max(-90.0f, Math.min(90.0f, freeLookPitch));
    }

    public static float freeLookYaw() {
        return freeLookYaw;
    }

    public static float freeLookPitch() {
        return freeLookPitch;
    }

    public static void trackAttack(int entityId) {
        long now = System.currentTimeMillis();
        if (entityId == comboEntityId && now < comboUntil) {
            comboCount++;
        } else {
            comboEntityId = entityId;
            comboCount = 1;
        }
        comboUntil = now + 1500;
    }

    public static int comboCount() {
        if (System.currentTimeMillis() >= comboUntil) {
            comboCount = 0;
            comboEntityId = -1;
        }
        return comboCount;
    }

    public static void tick(Minecraft client) {
        if (client.player == null) {
            freeLook = false;
            return;
        }
        ModConfig config = ModConfig.get();
        if (client.level != null) {
            if (config.timeChangerEnabled) {
                client.level.getLevelData().setDayTime(config.timeOfDay);
            }
            if (config.weatherChangerEnabled) {
                boolean raining = config.weatherMode == 0
                        ? client.level.getLevelData().isRaining()
                        : config.weatherMode == 2 || config.weatherMode == 3;
                boolean thundering = config.weatherMode == 0
                        ? client.level.getLevelData().isThundering()
                        : config.weatherMode == 3;
                client.level.setRainLevel(raining ? 1.0f : 0.0f);
                client.level.setThunderLevel(thundering ? 1.0f : 0.0f);
            }
        }
        if (client.screen != null) {
            freeLook = false;
            return;
        }
        if (!config.freeLookEnabled) freeLook = false;
        if (!config.toggleSprintEnabled) sprintToggled = false;
        if (!config.toggleSneakEnabled) sneakToggled = false;
    }
}
