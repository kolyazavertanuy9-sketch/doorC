package dev.crystalutility;

import dev.crystalutility.config.ModConfig;
import dev.crystalutility.FeatureState;
import dev.crystalutility.labels.BlockLabelEditorScreen;
import dev.crystalutility.labels.BlockLabelManager;
import dev.crystalutility.media.MediaController;
import dev.crystalutility.render.CombatHud;
import dev.crystalutility.render.BlockHighlightRenderer;
import dev.crystalutility.render.CursorTrailRenderer;
import dev.crystalutility.render.DamageParticleRenderer;
import dev.crystalutility.render.HitboxRenderer;
import dev.crystalutility.render.PvPHud;
import dev.crystalutility.render.ShieldCooldownTracker;
import dev.crystalutility.render.VisualEffectsHud;
import dev.crystalutility.text.UiText;
import dev.crystalutility.social.DiscordRichPresence;
import dev.crystalutility.skin.ClientSkinManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import dev.crystalutility.resourcepack.ResourcePackAutoLoader;

public final class CrystalUtilityClient implements ClientModInitializer {
    public static final String MOD_ID = "crystalutility";

    private static KeyMapping toggleHitboxes;
    private static KeyMapping toggleCrystalOptimizer;
    private static KeyMapping createBlockLabel;
    private static KeyMapping openConfig;
    private static KeyMapping mediaToggle;
    private static KeyMapping mediaNext;
    private static KeyMapping mediaPrevious;
    private static KeyMapping toggleFreeLook;
    private static KeyMapping toggleSprint;
    private static KeyMapping toggleSneak;
    private static KeyMapping smoothZoom;
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main"));

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        MediaController.start();
        DiscordRichPresence.start();
        ClientLifecycleEvents.CLIENT_STARTED.register(ResourcePackAutoLoader::activate);
        ClientLifecycleEvents.CLIENT_STARTED.register(ClientSkinManager::loadSavedSkin);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> DiscordRichPresence.stop());

        toggleHitboxes = register("key.crystalutility.hitboxes_toggle", GLFW.GLFW_KEY_B);
        toggleCrystalOptimizer = register("key.crystalutility.crystal_optimizer_toggle", GLFW.GLFW_KEY_UNKNOWN);
        createBlockLabel = register("key.crystalutility.create_block_label", GLFW.GLFW_KEY_L);
        openConfig = register("key.crystalutility.open_config", GLFW.GLFW_KEY_RIGHT_SHIFT);
        mediaToggle = register("key.crystalutility.media_toggle", GLFW.GLFW_KEY_UNKNOWN);
        mediaNext = register("key.crystalutility.media_next", GLFW.GLFW_KEY_UNKNOWN);
        mediaPrevious = register("key.crystalutility.media_previous", GLFW.GLFW_KEY_UNKNOWN);
        toggleFreeLook = register("key.crystalutility.freelook_toggle", GLFW.GLFW_KEY_V);
        toggleSprint = register("key.crystalutility.sprint_toggle", GLFW.GLFW_KEY_UNKNOWN);
        toggleSneak = register("key.crystalutility.sneak_toggle", GLFW.GLFW_KEY_UNKNOWN);
        smoothZoom = register("key.crystalutility.smooth_zoom", GLFW.GLFW_KEY_C);

        WorldRenderEvents.AFTER_ENTITIES.register(HitboxRenderer::render);
        WorldRenderEvents.AFTER_ENTITIES.register(BlockHighlightRenderer::render);
        WorldRenderEvents.AFTER_ENTITIES.register(BlockLabelManager::renderWorld);
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
                ScreenEvents.afterRender(screen).register((renderedScreen, graphics, mouseX, mouseY, tickDelta) -> {
                        CursorTrailRenderer.render(renderedScreen, graphics, mouseX, mouseY,
                                    ModConfig.parseColor(ModConfig.get().uiAccentColor, 0xFF3478F6));
                        dev.crystalutility.render.MediaHud.renderOnScreen(graphics, mouseX, mouseY);
                }));
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> VisualEffectsHud.onChunkLoad());
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            CombatHud.renderCrosshair(drawContext);
            CombatHud.renderTargetHud(drawContext);
            CombatHud.renderReachAndCombo(drawContext);
            DamageParticleRenderer.render(drawContext);
            PvPHud.render(drawContext);
            dev.crystalutility.render.MediaHud.render(drawContext);
            VisualEffectsHud.render(drawContext);
            BlockLabelManager.renderHud(drawContext);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleKeybinds(client);
            CrystalVisibility.tick(client);
            FeatureState.tick(client);
            ShieldCooldownTracker.tick(client);
            DamageParticleRenderer.tick(client);
            ClientSkinManager.tick(client);
            DiscordRichPresence.tick(client);
        });
    }

    private static KeyMapping register(String translationKey, int keyCode) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
                translationKey, InputConstants.Type.KEYSYM, keyCode, KEY_CATEGORY));
    }

    public static boolean isSmoothZoomHeld() {
        return smoothZoom != null && smoothZoom.isDown();
    }

    private static void handleKeybinds(Minecraft client) {
        while (openConfig.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(createConfigScreen(null));
            }
        }
        while (toggleHitboxes.consumeClick()) {
            ModConfig.get().hitboxesEnabled = !ModConfig.get().hitboxesEnabled;
            ModConfig.save();
        }
        while (toggleCrystalOptimizer.consumeClick()) {
            ModConfig.get().crystalOptimizationEnabled = !ModConfig.get().crystalOptimizationEnabled;
            ModConfig.save();
        }
        while (createBlockLabel.consumeClick()) {
            if (client.level == null) continue;
            if (client.hitResult instanceof BlockHitResult hitResult) {
                if (client.player != null && client.player.isShiftKeyDown()) {
                    BlockLabelManager.removeAt(hitResult.getBlockPos());
                    continue;
                }
                client.setScreen(BlockLabelEditorScreen.create(client.screen, hitResult.getBlockPos()));
            } else if (client.player != null) {
                client.player.displayClientMessage(Component.literal(
                        UiText.tr("Look at a block to place or remove a label.")), true);
            }
        }
        while (mediaToggle.consumeClick()) MediaController.send(MediaController.Command.TOGGLE);
        while (mediaNext.consumeClick()) MediaController.send(MediaController.Command.NEXT);
        while (mediaPrevious.consumeClick()) MediaController.send(MediaController.Command.PREVIOUS);
        while (toggleFreeLook.consumeClick()) {
            if (ModConfig.get().freeLookEnabled) FeatureState.toggleFreeLook(client);
        }
        while (toggleSprint.consumeClick()) {
            if (ModConfig.get().toggleSprintEnabled) FeatureState.toggleSprint();
        }
        while (toggleSneak.consumeClick()) {
            if (ModConfig.get().toggleSneakEnabled) FeatureState.toggleSneak();
        }
    }

    public static Screen createConfigScreen(Screen parent) {
        return dev.crystalutility.config.ConfigScreen.create(parent);
    }
}
