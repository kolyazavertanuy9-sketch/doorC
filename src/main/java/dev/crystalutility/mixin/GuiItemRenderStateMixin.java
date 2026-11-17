package dev.crystalutility.mixin;

import dev.crystalutility.render.ShieldCooldownRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.gui.render.state.GuiItemRenderState;

@Mixin(GuiItemRenderState.class)
public abstract class GuiItemRenderStateMixin implements ShieldCooldownRenderState {
    @Unique
    private boolean crystalutility$shieldDisabled;

    @Override
    public boolean crystalutility$isShieldDisabled() {
        return crystalutility$shieldDisabled;
    }

    @Override
    public void crystalutility$setShieldDisabled(boolean disabled) {
        crystalutility$shieldDisabled = disabled;
    }
}
