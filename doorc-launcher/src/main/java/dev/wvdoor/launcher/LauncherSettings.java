package dev.wvdoor.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;

public final class LauncherSettings {
    private static final String GAME_DIRECTORY = "gameDirectory";
    private static final String CONFIGURED = "configured";
    private static final String OFFLINE_PLAYER_NAME = "offlinePlayerName";

    private final Path settingsFile;
    private final Properties properties = new Properties();

    private LauncherSettings(Path settingsFile) {
        this.settingsFile = settingsFile;
    }

    public static LauncherSettings load() throws IOException {
        Path configDirectory = appDataDirectory().resolve("DoorCLauncher");
        Files.createDirectories(configDirectory);
        LauncherSettings settings = new LauncherSettings(configDirectory.resolve("launcher.properties"));
        if (Files.isRegularFile(settings.settingsFile)) {
            try (InputStream input = Files.newInputStream(settings.settingsFile)) {
                settings.properties.load(input);
            }
        } else {
            settings.properties.setProperty(GAME_DIRECTORY, defaultGameDirectory().toString());
            settings.properties.setProperty(CONFIGURED, "false");
        }
        return settings;
    }

    public static Optional<Path> applicationInstallDirectory() {
        if (System.getProperty("jpackage.app-version") == null) return Optional.empty();
        Path javaHome = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
        Path runtimeDirectory = javaHome.getFileName();
        if (runtimeDirectory == null || !"runtime".equalsIgnoreCase(runtimeDirectory.toString())) {
            return Optional.empty();
        }
        return Optional.ofNullable(javaHome.getParent());
    }

    public static boolean gameDataIsInsideApplication(Path directory) {
        return applicationInstallDirectory()
                .map(installDirectory -> GameDataMigration.isInsideInstallation(directory, installDirectory))
                .orElse(false);
    }

    public Path gameDirectory() {
        String path = properties.getProperty(GAME_DIRECTORY);
        return path == null || path.isBlank() ? defaultGameDirectory() : Path.of(path);
    }

    public boolean isConfigured() {
        return Boolean.parseBoolean(properties.getProperty(CONFIGURED, "false"));
    }

    public String offlinePlayerName() {
        return properties.getProperty(OFFLINE_PLAYER_NAME, "");
    }

    public void setOfflinePlayerName(String playerName) throws IOException {
        properties.setProperty(OFFLINE_PLAYER_NAME, playerName);
        save();
    }

    public void setGameDirectory(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (gameDataIsInsideApplication(normalized)) {
            throw new IllegalArgumentException(
                    "Choose a game-data folder outside the launcher installation folder.");
        }
        properties.setProperty(GAME_DIRECTORY, normalized.toString());
        properties.setProperty(CONFIGURED, "true");
        save();
    }

    public Path settingsFile() {
        return settingsFile;
    }

    private void save() throws IOException {
        Path temporary = settingsFile.resolveSibling(settingsFile.getFileName() + ".part");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "DoorC Launcher settings");
        }
        try {
            Files.move(temporary, settingsFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, settingsFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path defaultGameDirectory() {
        return appDataDirectory().resolve("DoorC");
    }

    static Path appDataDirectory() {
        String appData = System.getenv("APPDATA");
        return appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"))
                : Path.of(appData);
    }
}
