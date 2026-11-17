package dev.wvdoor.launcher.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameAccountTest {
    @Test
    void createsStableOfflineProfileCredentials() {
        GameAccount first = GameAccount.offline("Player_1");
        GameAccount second = GameAccount.offline("Player_1");

        assertEquals(GameAccount.Provider.OFFLINE, first.provider());
        assertEquals(first.uuid(), second.uuid());
        assertEquals("0", first.accessToken());
        assertEquals("legacy", first.userType());
    }

    @Test
    void rejectsOfflineNamesOutsideMinecraftNameRules() {
        assertThrows(IllegalArgumentException.class, () -> GameAccount.offline("ab"));
        assertThrows(IllegalArgumentException.class, () -> GameAccount.offline("player name"));
        assertThrows(IllegalArgumentException.class, () -> GameAccount.offline("name-that-is-too-long"));
    }
}
