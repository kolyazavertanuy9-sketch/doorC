package dev.crystalutility.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.crystalutility.labels.BlockLabel;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public final class ModConfig {
    public static final float UI_PANEL_OPACITY = 0.8f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("crystalutility.json");
    private static ModConfig instance = new ModConfig();

    public boolean crystalOptimizationEnabled = true;
    public boolean anchorOptimizationEnabled = true;
    public boolean removeCrystalParticles = true;
    public boolean hideAttackedCrystalsImmediately = true;
    public boolean hitboxesEnabled = true;
    public boolean renderEyeLines = true;
    public boolean renderFacingVectors = true;
    public boolean customCrosshairEnabled = true;
    public boolean targetHudEnabled = true;
    public boolean targetHitboxHighlightEnabled = true;
    public boolean keystrokesHudEnabled = true;
    public boolean cpsHudEnabled = true;
    public boolean coordinatesHudEnabled = true;
    public boolean inventoryItemsHudEnabled = true;
    public boolean armorHudEnabled = true;
    public boolean shieldModelColorEnabled = true;
    public boolean effectsHudEnabled = true;
    public boolean antiDarknessEnabled = true;
    public boolean showMediaHud = true;
    public boolean discordRichPresenceEnabled = true;
    public boolean fullbrightEnabled;
    public boolean timeChangerEnabled;
    public boolean weatherChangerEnabled;
    public boolean particleChangerEnabled;
    public boolean freeLookEnabled = true;
    public boolean toggleSprintEnabled;
    public boolean toggleSneakEnabled;
    public boolean motionBlurEnabled;
    public boolean chunkAnimatorEnabled;
    public boolean reachDisplayEnabled;
    public boolean comboCounterEnabled;
    public boolean blockOutlineEnabled = true;
    public boolean blockHighlightEnabled = true;
    public boolean damageParticlesEnabled = true;
    public boolean blockLabelsEnabled = true;
    public boolean blockLabelHighlightEnabled = true;
    public String blockHighlightColor = "FF3478F6";
    public String damageParticleColor = "FFFF5363";
    public String blockLabelColor = "FFFFC857";
    public int blockLabelRange = 96;
    public ArrayList<BlockLabel> blockLabels = new ArrayList<>();
    public boolean customSkinEnabled;
    public boolean customSkinSlimModel;
    public boolean customSkinUploadCompleted;
    public boolean customSkinUploadAllowed = true;
    public String customSkinFile = "";
    public int timeOfDay = 6000;
    public int weatherMode = 0;
    public int particleMode = 1;
    public float motionBlurStrength = 0.15f;
    public float hudCoordinatesScale = 1.0f;
    public float hudKeystrokesScale = 1.0f;
    public float hudCpsScale = 1.0f;
    public float hudInventoryItemsScale = 1.0f;
    public float hudArmorScale = 1.0f;
    public float hudEffectsScale = 1.0f;
    public float hudMediaScale = 1.0f;
    public float hudTargetScale = 1.0f;
    public float hudReachScale = 1.0f;
    public float hudComboScale = 1.0f;
    public int hudReachX = -1;
    public int hudReachY = -1;
    public int hudComboX = -1;
    public int hudComboY = -1;
    public String hitboxColor = "CC55AAFF";
    public String eyeColor = "FFFFCC33";
    public String facingColor = "FF55FF88";
    public String crosshairColor = "FFFFFFFF";
    public String crosshairTargetColor = "FFFF566B";
    public String targetHitboxColor = "FFFF566B";
    public String shieldModelReadyColor = "FF39D98A";
    public String shieldModelBrokenColor = "FFFF4D5A";
    public String uiBackgroundColor = "FF090B12";
    public String uiAccentColor = "FF3478F6";
    public String uiTextColor = "FFF1F5FC";
    public String uiMutedColor = "FF9BA9BB";
    public int hudCoordinatesX = -1;
    public int hudCoordinatesY = -1;
    public int hudKeystrokesX = -1;
    public int hudKeystrokesY = -1;
    public int hudCpsX = -1;
    public int hudCpsY = -1;
    public int hudInventoryItemsX = -1;
    public int hudInventoryItemsY = -1;
    public int hudArmorX = -1;
    public int hudArmorY = -1;
    public int hudEffectsX = -1;
    public int hudEffectsY = -1;
    public int hudMediaX = -1;
    public int hudMediaY = -1;
    public int hudTargetX = -1;
    public int hudTargetY = -1;
    public float lineThickness = 1.5f;
    public float crosshairSize = 5.0f;
    public float crosshairGap = 3.0f;
    public float crosshairThickness = 1.5f;
    public int hitboxRange = 96;

    private ModConfig() {}

    public static ModConfig get() {
        return instance;
    }

    public static synchronized void load() {
        if (!Files.exists(FILE)) {
            save();
            return;
        }
        try {
            ModConfig loaded = GSON.fromJson(Files.readString(FILE), ModConfig.class);
            if (loaded != null) instance = loaded;
        } catch (IOException | RuntimeException exception) {
            System.err.println("[wvDoorClient] Could not read config " + FILE + ": " + exception.getMessage());
        }
        instance.sanitize();
    }

    public static synchronized void save() {
        instance.sanitize();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (IOException exception) {
            System.err.println("[wvDoorClient] Could not save config " + FILE + ": " + exception.getMessage());
        }
    }

    private void sanitize() {
        lineThickness = Math.max(0.5f, Math.min(5.0f, lineThickness));
        crosshairSize = Math.max(2.0f, Math.min(12.0f, crosshairSize));
        crosshairGap = Math.max(1.0f, Math.min(8.0f, crosshairGap));
        crosshairThickness = Math.max(1.0f, Math.min(4.0f, crosshairThickness));
        hitboxRange = Math.max(8, Math.min(256, hitboxRange));
        timeOfDay = Math.max(0, Math.min(23999, timeOfDay));
        weatherMode = Math.max(0, Math.min(3, weatherMode));
        particleMode = Math.max(0, Math.min(2, particleMode));
        motionBlurStrength = Math.max(0.02f, Math.min(0.5f, motionBlurStrength));
        blockLabelRange = Math.max(8, Math.min(256, blockLabelRange));
        if (blockLabels == null) {
            blockLabels = new ArrayList<>();
        }
        blockLabels.removeIf(label -> label == null || label.text == null || label.text.isBlank() || label.x == 0 && label.y == 0 && label.z == 0 && label.text.equals(""));
        for (BlockLabel label : blockLabels) {
            if (label.text == null || label.text.isBlank()) label.text = "Marker";
            label.text = label.text.trim();
        }
        blockLabelColor = normalizeColor(blockLabelColor, "FFFFC857");
        blockHighlightColor = normalizeColor(blockHighlightColor, "FF3478F6");
        damageParticleColor = normalizeColor(damageParticleColor, "FFFF5363");
        hudCoordinatesScale = sanitizeHudScale(hudCoordinatesScale);
        hudKeystrokesScale = sanitizeHudScale(hudKeystrokesScale);
        hudCpsScale = sanitizeHudScale(hudCpsScale);
        hudArmorScale = sanitizeHudScale(hudArmorScale);
        hudEffectsScale = sanitizeHudScale(hudEffectsScale);
        hudMediaScale = sanitizeHudScale(hudMediaScale);
        hudTargetScale = sanitizeHudScale(hudTargetScale);
        hudReachScale = sanitizeHudScale(hudReachScale);
        hudComboScale = sanitizeHudScale(hudComboScale);
        hitboxColor = normalizeColor(hitboxColor, "CC55AAFF");
        eyeColor = normalizeColor(eyeColor, "FFFFCC33");
        facingColor = normalizeColor(facingColor, "FF55FF88");
        crosshairColor = normalizeColor(crosshairColor, "FFFFFFFF");
        crosshairTargetColor = normalizeColor(crosshairTargetColor, "FFFF566B");
        targetHitboxColor = normalizeColor(targetHitboxColor, "FFFF566B");
        shieldModelReadyColor = normalizeColor(shieldModelReadyColor, "FF39D98A");
        shieldModelBrokenColor = normalizeColor(shieldModelBrokenColor, "FFFF4D5A");
        uiBackgroundColor = normalizeColor(uiBackgroundColor, "FF090B12");
        uiAccentColor = normalizeColor(uiAccentColor, "FF3478F6");
        uiTextColor = normalizeColor(uiTextColor, "FFF1F5FC");
        uiMutedColor = normalizeColor(uiMutedColor, "FF9BA9BB");
        if (customSkinFile == null || customSkinFile.contains("/") || customSkinFile.contains("\\")
                || customSkinFile.equals(".") || customSkinFile.equals("..")) {
            customSkinFile = "";
        }
    }

    private static float sanitizeHudScale(float scale) {
        return Float.isFinite(scale) ? Math.max(0.5f, Math.min(2.0f, scale)) : 1.0f;
    }

    public static int parseColor(String value, int fallback) {
        try {
            String hex = value == null ? "" : value.trim().replaceFirst("^#", "");
            if (hex.length() == 6) hex = "FF" + hex;
            if (hex.length() != 8) return fallback;
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String normalizeColor(String value, String fallback) {
        int parsed = parseColor(value, parseColor(fallback, 0xFFFFFFFF));
        return String.format("%08X", parsed);
    }
}
