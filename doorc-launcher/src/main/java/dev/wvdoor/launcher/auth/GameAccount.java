package dev.wvdoor.launcher.auth;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public record GameAccount(Provider provider, String playerName, String uuid,
                          String accessToken, String userType) {
    public GameAccount {
        Objects.requireNonNull(provider, "provider");
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("The account profile has no player name.");
        }
        uuid = canonicalUuid(uuid);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("The account has no access token.");
        }
        if (userType == null || userType.isBlank()) {
            throw new IllegalArgumentException("The account has no user type.");
        }
    }

    public static GameAccount elyBy(String playerName, String uuid, String accessToken) {
        return new GameAccount(Provider.ELY_BY, playerName, uuid, accessToken, "legacy");
    }

    public static GameAccount offline(String playerName) {
        if (playerName == null || !playerName.matches("[A-Za-z0-9_]{3,16}")) {
            throw new IllegalArgumentException("Offline names must be 3-16 letters, numbers, or underscores.");
        }
        UUID offlineUuid = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
        return new GameAccount(Provider.OFFLINE, playerName, offlineUuid.toString(), "0", "legacy");
    }

    @Override
    public String toString() {
        return "GameAccount[provider=" + provider + ", playerName=" + playerName + "]";
    }

    private static String canonicalUuid(String value) {
        if (value == null) throw new IllegalArgumentException("The account profile has no UUID.");
        String compact = value.replace("-", "");
        if (!compact.matches("(?i)[0-9a-f]{32}")) {
            throw new IllegalArgumentException("The account profile UUID is invalid.");
        }
        return UUID.fromString(compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-"
                + compact.substring(12, 16) + "-" + compact.substring(16, 20) + "-"
                + compact.substring(20)).toString();
    }

    public enum Provider {
        OFFLINE("Offline"),
        ELY_BY("Ely.by"),
        MICROSOFT("Microsoft");

        private final String label;

        Provider(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
