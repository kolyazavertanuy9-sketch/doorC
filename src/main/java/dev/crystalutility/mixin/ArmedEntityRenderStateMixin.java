package dev.crystalutility.mixin;

import dev.crystalutility.render.ShieldCooldownRenderState;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ArmedEntityRenderState.class)
public abstract class ArmedEntityRenderStateMixin implements ShieldCooldownRenderState {
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
