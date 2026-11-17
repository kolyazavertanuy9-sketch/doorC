package dev.crystalutility.render;

import dev.crystalutility.config.ModConfig;
import dev.wvlib.api.ThemeColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.crystalutility.text.UiText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PvPHud {
    private static final int MUTED = 0xFF9BA5B4;

    private PvPHud() {}

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        ModConfig config = ModConfig.get();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        if (config.coordinatesHudEnabled) {
            HudLayout.withScale(graphics, config, HudLayout.Widget.COORDINATES, screenWidth, screenHeight,
                    () -> renderCoordinates(graphics, minecraft));
        }
        if (config.effectsHudEnabled) {
            HudLayout.withScale(graphics, config, HudLayout.Widget.EFFECTS, screenWidth, screenHeight,
                    () -> renderEffects(graphics, minecraft));
        }
        if (config.keystrokesHudEnabled) {
            HudLayout.withScale(graphics, config, HudLayout.Widget.KEYSTROKES, screenWidth, screenHeight,
                    () -> renderKeystrokes(graphics, minecraft));
        }
        if (config.inventoryItemsHudEnabled) {
            HudLayout.withScale(graphics, config, HudLayout.Widget.ITEMS, screenWidth, screenHeight,
                    () -> renderInventoryItems(graphics, minecraft));
        }
        if (config.cpsHudEnabled) {
            HudLayout.withScale(graphics, config, HudLayout.Widget.CPS, screenWidth, screenHeight,
                    () -> renderCps(graphics, minecraft, config));
        }
        if (config.armorHudEnabled) {
            HudLayout.withScale(graphics, config, HudLayout.Widget.ARMOR, screenWidth, screenHeight,
                    () -> renderArmor(graphics, minecraft));
        }
    }

    private static void renderCoordinates(GuiGraphics graphics, Minecraft minecraft) {
        var player = minecraft.player;
        int x = HudLayout.x(ModConfig.get(), HudLayout.Widget.COORDINATES,
                graphics.guiWidth(), graphics.guiHeight());
        int y = HudLayout.y(ModConfig.get(), HudLayout.Widget.COORDINATES,
                graphics.guiWidth(), graphics.guiHeight());
        panel(graphics, x, y, HudLayout.width(HudLayout.Widget.COORDINATES), 44);
        label(graphics, UiText.tr("POSITION"), x + 9, y + 6);
        text(graphics, String.format(Locale.ROOT, "X %.1f   Y %.1f   Z %.1f",
                player.getX(), player.getY(), player.getZ()), x + 9, y + 18, 0xFFF4F6FA);
        text(graphics, UiText.tr("Facing") + "  " + direction(player.getYRot()), x + 9, y + 31,
                ThemeColors.accent(ModConfig.get().uiAccentColor));
    }

    private static void renderKeystrokes(GuiGraphics graphics, Minecraft minecraft) {
        ModConfig config = ModConfig.get();
        int x = HudLayout.x(config, HudLayout.Widget.KEYSTROKES, graphics.guiWidth(), graphics.guiHeight());
        int y = HudLayout.y(config, HudLayout.Widget.KEYSTROKES, graphics.guiWidth(), graphics.guiHeight());
        int accent = ThemeColors.accent(config.uiAccentColor);
        drawKey(graphics, minecraft.options.keyUp.isDown(), x + 28, y, 24, 24, "W", accent);
        drawKey(graphics, minecraft.options.keyLeft.isDown(), x, y + 26, 24, 24, "A", accent);
        drawKey(graphics, minecraft.options.keyDown.isDown(), x + 28, y + 26, 24, 24, "S", accent);
        drawKey(graphics, minecraft.options.keyRight.isDown(), x + 56, y + 26, 24, 24, "D", accent);
        drawKey(graphics, minecraft.options.keyJump.isDown(), x, y + 52, 80, 18, "SPACE", accent);
        drawKey(graphics, minecraft.options.keyAttack.isDown(), x, y + 73, 38, 18, "LMB", accent);
        drawKey(graphics, minecraft.options.keyUse.isDown(), x + 42, y + 73, 38, 18, "RMB", accent);
    }

    private static void renderCps(GuiGraphics graphics, Minecraft minecraft, ModConfig config) {
        int cpsX = HudLayout.x(config, HudLayout.Widget.CPS, graphics.guiWidth(), graphics.guiHeight());
        int cpsY = HudLayout.y(config, HudLayout.Widget.CPS, graphics.guiWidth(), graphics.guiHeight());
        SmoothHudRenderer.drawText(graphics, "CPS", cpsX + 4, cpsY + 7, 0xFF6D9FEA);
        SmoothHudRenderer.drawText(graphics, "L " + ClickTracker.cps(0), cpsX + 34, cpsY + 7, 0xFFE9F1FF);
        SmoothHudRenderer.drawText(graphics, "R " + ClickTracker.cps(1), cpsX + 65, cpsY + 7, 0xFFE9F1FF);
        SmoothHudRenderer.drawRoundedRect(graphics, cpsX + 4, cpsY + 24, 92, 2, 1, 0x553478F6);
    }

    private static void renderInventoryItems(GuiGraphics graphics, Minecraft minecraft) {
        ItemStack[] trackedItems = {
                new ItemStack(Items.TOTEM_OF_UNDYING),
                new ItemStack(Items.GOLDEN_APPLE),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
                new ItemStack(Items.ENDER_PEARL),
                new ItemStack(Items.END_CRYSTAL),
                new ItemStack(Items.OBSIDIAN)
        };
        int x = HudLayout.x(ModConfig.get(), HudLayout.Widget.ITEMS, graphics.guiWidth(), graphics.guiHeight());
        int y = HudLayout.y(ModConfig.get(), HudLayout.Widget.ITEMS, graphics.guiWidth(), graphics.guiHeight());
        int accent = ThemeColors.accent(ModConfig.get().uiAccentColor);
        for (int i = 0; i < trackedItems.length; i++) {
            ItemStack icon = trackedItems[i];
            int count = 0;
            for (ItemStack stack : minecraft.player.getInventory().getNonEquipmentItems()) {
                if (ItemStack.isSameItem(stack, icon)) count += stack.getCount();
            }

            int itemX = x + i * 47;
            SmoothHudRenderer.drawRoundedRect(graphics, itemX, y + 2, 43, 24, 7, 0xA60D121A);
            SmoothHudRenderer.drawRoundedRect(graphics, itemX, y + 1, 43, 2, 1,
                    (accent & 0x00FFFFFF) | 0x7A000000);
            graphics.renderItem(icon, itemX + 3, y + 5);
            String label = count > 99 ? "99+" : Integer.toString(count);
            int textWidth = minecraft.font.width(label);
            graphics.drawString(minecraft.font, label, itemX + 39 - textWidth, y + 9,
                    count > 0 ? 0xFFF0F4FC : 0xFF8993A2, false);
        }
    }

    private static void drawKey(GuiGraphics graphics, boolean down,
                                int x, int y, int width, int height, String key, int accent) {
        SmoothHudRenderer.drawKey(graphics, x, y, width, height, key, down, accent);
    }

    private static void renderArmor(GuiGraphics graphics, Minecraft minecraft) {
        List<ItemStack> armor = List.of(
                minecraft.player.getItemBySlot(EquipmentSlot.HEAD),
                minecraft.player.getItemBySlot(EquipmentSlot.CHEST),
                minecraft.player.getItemBySlot(EquipmentSlot.LEGS),
                minecraft.player.getItemBySlot(EquipmentSlot.FEET));
        int x = HudLayout.x(ModConfig.get(), HudLayout.Widget.ARMOR,
                graphics.guiWidth(), graphics.guiHeight());
        int y = HudLayout.y(ModConfig.get(), HudLayout.Widget.ARMOR,
                graphics.guiWidth(), graphics.guiHeight());
        panel(graphics, x, y, HudLayout.width(HudLayout.Widget.ARMOR),
                HudLayout.baseHeight(ModConfig.get(), HudLayout.Widget.ARMOR));
        label(graphics, UiText.tr("ARMOR"), x + 8, y + 4);
        for (int i = 0; i < armor.size(); i++) {
            ItemStack stack = armor.get(i);
            int slotX = x + 8 + i * 22;
            SmoothHudRenderer.drawRoundedRect(graphics, slotX, y + 14, 20, 20, 5, 0xA8202733);
            if (stack.isEmpty()) {
                graphics.drawCenteredString(minecraft.font, new String[]{"H", "C", "L", "B"}[i],
                        slotX + 10, y + 20, MUTED);
                continue;
            }
            graphics.renderItem(stack, slotX + 2, y + 16);
            if (!stack.isDamageableItem()) continue;
            int durability = Math.max(0, 100 - stack.getDamageValue() * 100 / stack.getMaxDamage());
            int color = durability > 50 ? 0xFF65D68A : durability > 20 ? 0xFFFFC857 : 0xFFFF566B;
            SmoothHudRenderer.drawRoundedRect(graphics, slotX + 2, y + 35, 16, 2, 1, 0xFF343944);
            SmoothHudRenderer.drawRoundedRect(graphics, slotX + 2, y + 35,
                    durability * 16 / 100, 2, 1, color);
        }
    }

    private static void renderEffects(GuiGraphics graphics, Minecraft minecraft) {
        List<MobEffectInstance> effects = minecraft.player.getActiveEffects().stream()
                .sorted(Comparator.comparingInt(MobEffectInstance::getDuration))
                .limit(5)
                .toList();
        if (effects.isEmpty()) return;

        int width = 142;
        int rowHeight = 24;
        int x = HudLayout.x(ModConfig.get(), HudLayout.Widget.EFFECTS,
                graphics.guiWidth(), graphics.guiHeight());
        int y = HudLayout.y(ModConfig.get(), HudLayout.Widget.EFFECTS,
                graphics.guiWidth(), graphics.guiHeight());
        panel(graphics, x, y, width, 18 + effects.size() * rowHeight);
        label(graphics, UiText.tr("ACTIVE EFFECTS"), x + 8, y + 5);
        for (int i = 0; i < effects.size(); i++) {
            MobEffectInstance instance = effects.get(i);
            int rowY = y + 20 + i * rowHeight;
            String name = instance.getEffect().value().getDisplayName().getString();
            int amplifier = instance.getAmplifier();
            if (amplifier > 0) name += " " + roman(amplifier + 1);
            int color = 0xFF000000 | instance.getEffect().value().getColor();
            Identifier effectId = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value());
            Identifier icon = Identifier.fromNamespaceAndPath("minecraft",
                    "textures/mob_effect/" + effectId.getPath() + ".png");
            if (minecraft.getResourceManager().getResource(icon).isPresent()) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x + 8, rowY + 2,
                        0, 0, 18, 18, 18, 18);
            } else {
                graphics.fill(x + 9, rowY + 4, x + 21, rowY + 16, color);
            }
            text(graphics, truncate(minecraft, name, width - 50), x + 30, rowY + 1, 0xFFF4F6FA);
            text(graphics, duration(instance), x + 30, rowY + 12, MUTED);
        }
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        ThemedUiRenderer.drawGlassPanel(graphics, x, y, width, height);
    }

    private static void label(GuiGraphics graphics, String value, int x, int y) {
        text(graphics, value, x, y, MUTED);
    }

    private static void text(GuiGraphics graphics, String value, int x, int y, int color) {
        graphics.drawString(Minecraft.getInstance().font, Component.literal(value), x, y, color, false);
    }

    private static String direction(float yaw) {
        String[] directions = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        int index = Math.floorMod(Math.round(yaw / 45.0f), directions.length);
        return directions[index];
    }

    private static String duration(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) return "∞";
        int seconds = Math.max(0, effect.getDuration() / 20);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static String roman(int amplifier) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return amplifier > 0 && amplifier <= numerals.length ? numerals[amplifier - 1] : Integer.toString(amplifier);
    }

    private static String truncate(Minecraft minecraft, String value, int maxWidth) {
        if (minecraft.font.width(value) <= maxWidth) return value;
        while (!value.isEmpty() && minecraft.font.width(value + "...") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "...";
    }
}
