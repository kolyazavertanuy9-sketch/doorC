package dev.crystalutility.mixin;

import dev.crystalutility.render.ClickTracker;
import dev.crystalutility.render.MediaHud;
import dev.crystalutility.FeatureState;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.glfw.GLFW;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void crystalutility$recordMouseClick(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        int button = buttonInfo.button();
        if (action == GLFW.GLFW_PRESS && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            MouseHandler mouseHandler = (MouseHandler) (Object) this;
            if (MediaHud.handleClick(
                    mouseHandler.getScaledXPos(net.minecraft.client.Minecraft.getInstance().getWindow()),
                    mouseHandler.getScaledYPos(net.minecraft.client.Minecraft.getInstance().getWindow()))) {
                ci.cancel();
                return;
            }
        }
        if (action == GLFW.GLFW_PRESS && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            ClickTracker.record(button);
        }
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void crystalutility$turnFreeLookCamera(CallbackInfo ci) {
        if (!FeatureState.freeLook()) return;
        FeatureState.rotateFreeLook(accumulatedDX, accumulatedDY);
        ci.cancel();
    }
}
