package dev.crystalutility.render;

import dev.crystalutility.config.ModConfig;
import dev.crystalutility.FeatureState;
import dev.crystalutility.render.HudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
import dev.crystalutility.text.UiText;

public final class CombatHud {
    private static final int TARGET_PANEL_WIDTH = 150;
    private static final int TARGET_PANEL_HEIGHT = 36;

    private CombatHud() {}

    public static void renderCrosshair(GuiGraphics graphics) {
        ModConfig config = ModConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null
                || minecraft.options.getCameraType().isFirstPerson() == false) return;

        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        if (config.customCrosshairEnabled) {
            Entity target = minecraft.crosshairPickEntity;
            boolean targetingLiving = target instanceof LivingEntity living && living.isAlive();
            int argb = ModConfig.parseColor(targetingLiving ? config.crosshairTargetColor : config.crosshairColor,
                    targetingLiving ? 0xFFFF566B : 0xFFFFFFFF);
            int color = withOpacity(argb, 0.96f);
            int thickness = Math.max(2, Math.round(config.crosshairThickness));
            int length = Math.max(3, Math.round(config.crosshairSize));
            int gap = Math.max(1, Math.round(config.crosshairGap));

            int radius = Math.max(1, thickness / 2);
            SmoothHudRenderer.drawRoundedRect(graphics, centerX - length - gap, centerY - thickness / 2,
                    length, thickness, radius, color);
            SmoothHudRenderer.drawRoundedRect(graphics, centerX + gap, centerY - thickness / 2,
                    length, thickness, radius, color);
            SmoothHudRenderer.drawRoundedRect(graphics, centerX - thickness / 2, centerY - length - gap,
                    thickness, length, radius, color);
            SmoothHudRenderer.drawRoundedRect(graphics, centerX - thickness / 2, centerY + gap,
                    thickness, length, radius, color);
            SmoothHudRenderer.drawRoundedRect(graphics, centerX - 1, centerY - 1, 2, 2, 1, color);
        }

        renderArrowCount(graphics, minecraft, centerX, centerY);
    }

    private static void renderArrowCount(GuiGraphics graphics, Minecraft minecraft, int centerX, int centerY) {
        var player = minecraft.player;
        if (player == null) return;
        boolean hasBow = player.getMainHandItem().is(Items.BOW) || player.getOffhandItem().is(Items.BOW)
                || player.getMainHandItem().is(Items.CROSSBOW) || player.getOffhandItem().is(Items.CROSSBOW);
        if (!hasBow) return;

        int arrows = 0;
        for (var stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.getItem() instanceof ArrowItem) arrows += stack.getCount();
        }

        int x = centerX + 12;
        int y = centerY + 12;
        String arrowCount = Integer.toString(arrows);
        int width = Math.max(39, 26 + minecraft.font.width(arrowCount));
        ThemedUiRenderer.drawGlassPanel(graphics, x, y, width, 20);
        graphics.renderItem(new net.minecraft.world.item.ItemStack(Items.ARROW), x + 2, y + 2);
        graphics.drawString(minecraft.font, arrowCount, x + 20, y + 6,
                ThemedUiRenderer.textColor(), false);
    }

    public static void renderTargetHud(GuiGraphics graphics) {
        if (!ModConfig.get().targetHudEnabled) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null
                || !(minecraft.crosshairPickEntity instanceof LivingEntity target)
                || !target.isAlive() || target == minecraft.player) return;

        int x = HudLayout.x(ModConfig.get(), HudLayout.Widget.TARGET,
                graphics.guiWidth(), graphics.guiHeight());
        int y = HudLayout.y(ModConfig.get(), HudLayout.Widget.TARGET,
                graphics.guiWidth(), graphics.guiHeight());
        int distanceTenths = Math.round(minecraft.player.distanceTo(target) * 10.0f);
        String title = truncate(minecraft, target.getName().getString(), TARGET_PANEL_WIDTH - 16);
        String metadata = truncate(minecraft, UiText.tr("%s blocks  |  %s / %s HP",
                String.format(java.util.Locale.ROOT, "%.1f", distanceTenths / 10.0f),
                String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, target.getHealth())),
                String.format(java.util.Locale.ROOT, "%.1f", target.getMaxHealth())),
                TARGET_PANEL_WIDTH - 16);

        HudLayout.withScale(graphics, ModConfig.get(), HudLayout.Widget.TARGET,
                graphics.guiWidth(), graphics.guiHeight(), () -> {
                    ThemedUiRenderer.drawGlassPanel(graphics, x, y, TARGET_PANEL_WIDTH, TARGET_PANEL_HEIGHT);
                    graphics.drawString(minecraft.font, Component.literal(title), x + 9, y + 6,
                            0xFFF4F6FA, false);
                    graphics.drawString(minecraft.font, Component.literal(metadata), x + 9, y + 19,
                            0xFFD3D9E2, false);

                    int barX = x + 9;
                    int barY = y + TARGET_PANEL_HEIGHT - 7;
                    int barWidth = TARGET_PANEL_WIDTH - 18;
                    SmoothHudRenderer.drawRoundedRect(graphics, barX, barY, barWidth, 3, 1, 0xA0343944);
                    float health = target.getMaxHealth() > 0
                            ? Math.max(0, Math.min(1, target.getHealth() / target.getMaxHealth()))
                            : 0;
                    int healthColor = health > 0.5f ? 0xFF65D68A
                            : health > 0.25f ? 0xFFFFC857 : 0xFFFF566B;
                    SmoothHudRenderer.drawRoundedRect(graphics, barX, barY,
                            Math.round(barWidth * health), 3, 1, healthColor);
                });
    }

    public static void renderReachAndCombo(GuiGraphics graphics) {
        ModConfig config = ModConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) return;
        if (config.reachDisplayEnabled && minecraft.hitResult != null
                && minecraft.hitResult.getType() != HitResult.Type.MISS) {
            int x = HudLayout.x(config, HudLayout.Widget.REACH, graphics.guiWidth(), graphics.guiHeight());
            int y = HudLayout.y(config, HudLayout.Widget.REACH, graphics.guiWidth(), graphics.guiHeight());
            double reach = minecraft.player.getEyePosition().distanceTo(minecraft.hitResult.getLocation());
            HudLayout.withScale(graphics, config, HudLayout.Widget.REACH,
                    graphics.guiWidth(), graphics.guiHeight(), () -> {
                        ThemedUiRenderer.drawGlassPanel(graphics, x, y, 84, 26);
                        graphics.drawString(minecraft.font, UiText.tr("REACH"), x + 8, y + 4, 0xFF9BA5B4, false);
                        graphics.drawString(minecraft.font,
                                UiText.tr("%s blocks", String.format(java.util.Locale.ROOT, "%.2f", reach)),
                                x + 8, y + 14, 0xFFF4F6FA, false);
                    });
        }
        int combo = FeatureState.comboCount();
        if (config.comboCounterEnabled && combo > 0) {
            int x = HudLayout.x(config, HudLayout.Widget.COMBO, graphics.guiWidth(), graphics.guiHeight());
            int y = HudLayout.y(config, HudLayout.Widget.COMBO, graphics.guiWidth(), graphics.guiHeight());
            HudLayout.withScale(graphics, config, HudLayout.Widget.COMBO,
                    graphics.guiWidth(), graphics.guiHeight(), () -> {
                        ThemedUiRenderer.drawGlassPanel(graphics, x, y, 100, 28);
                        graphics.drawString(minecraft.font, UiText.tr("COMBO  x%s", combo),
                                x + 9, y + 9, 0xFFFFFFFF, false);
                    });
        }
    }

    private static int withOpacity(int argb, float opacity) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * opacity);
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    private static String truncate(Minecraft minecraft, String value, int maxWidth) {
        if (minecraft.font.width(value) <= maxWidth) return value;
        String ellipsis = "...";
        while (!value.isEmpty() && minecraft.font.width(value + ellipsis) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + ellipsis;
    }
}
