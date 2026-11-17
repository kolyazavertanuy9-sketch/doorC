package dev.crystalutility.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ShieldCooldownTracker {
    private static final int ESTIMATED_COOLDOWN_TICKS = 100;
    private static final int RECENT_BLOCK_TICKS = 2;
    private static final Map<UUID, PlayerState> PLAYER_STATES = new HashMap<>();
    private static ClientLevel trackedLevel;

    private ShieldCooldownTracker() {}

    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null) {
            PLAYER_STATES.clear();
            trackedLevel = null;
            return;
        }
        if (trackedLevel != level) {
            PLAYER_STATES.clear();
            trackedLevel = level;
        }

        Set<UUID> seenPlayers = new HashSet<>();
        for (Player player : level.players()) {
            UUID id = player.getUUID();
            seenPlayers.add(id);
            PlayerState state = PLAYER_STATES.computeIfAbsent(id, ignored -> new PlayerState());
            if (state.disabledTicks > 0) state.disabledTicks--;

            boolean blockingWithShield = player.isBlocking()
                    && (player.getOffhandItem().is(Items.SHIELD) || player.getMainHandItem().is(Items.SHIELD));
            boolean recentlyBlocking = blockingWithShield || state.wasBlocking
                    || state.ticksSinceShieldBlock <= RECENT_BLOCK_TICKS;
            boolean tookDamage = state.initialized && player.getHealth() < state.lastHealth;
            if (tookDamage && recentlyBlocking) {
                state.damageNearShieldBlockTicks = RECENT_BLOCK_TICKS;
            } else if (state.damageNearShieldBlockTicks > 0) {
                state.damageNearShieldBlockTicks--;
            }

            boolean stoppedBlocking = state.initialized && state.wasBlocking && !blockingWithShield;
            boolean recentlyStoppedBlocking = !blockingWithShield
                    && state.ticksSinceShieldBlock <= RECENT_BLOCK_TICKS;
            if ((stoppedBlocking || recentlyStoppedBlocking)
                    && state.damageNearShieldBlockTicks > 0) {
                state.disabledTicks = ESTIMATED_COOLDOWN_TICKS;
                state.damageNearShieldBlockTicks = 0;
            }
            state.ticksSinceShieldBlock = blockingWithShield
                    ? 0 : Math.min(RECENT_BLOCK_TICKS + 1, state.ticksSinceShieldBlock + 1);
            state.wasBlocking = blockingWithShield;
            state.lastHealth = player.getHealth();
            state.initialized = true;
        }
        PLAYER_STATES.keySet().retainAll(seenPlayers);
    }

    public static boolean isDisabled(Player player) {
        if ((player.getOffhandItem().is(Items.SHIELD)
                && player.getCooldowns().isOnCooldown(player.getOffhandItem()))
                || (player.getMainHandItem().is(Items.SHIELD)
                && player.getCooldowns().isOnCooldown(player.getMainHandItem()))) {
            return true;
        }
        PlayerState state = PLAYER_STATES.get(player.getUUID());
        return state != null && state.disabledTicks > 0;
    }

    private static final class PlayerState {
        private boolean initialized;
        private boolean wasBlocking;
        private int disabledTicks;
        private int ticksSinceShieldBlock = RECENT_BLOCK_TICKS + 1;
        private int damageNearShieldBlockTicks;
        private float lastHealth;
    }
}
