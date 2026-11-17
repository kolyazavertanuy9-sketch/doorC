package dev.crystalutility.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public final class ShieldRenderContext {
    private static final ThreadLocal<Boolean> SHIELD_DISABLED = ThreadLocal.withInitial(() -> false);

    private ShieldRenderContext() {}

    public static void submit(
            ItemStackRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            int overlay,
            int outlineColor,
            boolean shieldDisabled) {
        withShieldDisabled(shieldDisabled,
                () -> state.submit(poseStack, collector, light, overlay, outlineColor));
    }

    public static void withShieldDisabled(boolean shieldDisabled, Runnable action) {
        boolean previous = SHIELD_DISABLED.get();
        SHIELD_DISABLED.set(shieldDisabled);
        try {
            action.run();
        } finally {
            SHIELD_DISABLED.set(previous);
        }
    }

    public static boolean isShieldDisabled() {
        return SHIELD_DISABLED.get();
    }
}
