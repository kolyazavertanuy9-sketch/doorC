package dev.wvdoor.launcher.content;

import java.nio.file.Path;
import java.util.Locale;

public record MinecraftProfile(String name, Path directory, String gameVersion,
                               String loader, String loaderVersion) {
    public MinecraftProfile {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Profile name is required.");
        if (directory == null) throw new IllegalArgumentException("Profile directory is required.");
        if (gameVersion == null || gameVersion.isBlank()) {
            throw new IllegalArgumentException("Minecraft version is required.");
        }
        loader = loader == null || loader.isBlank() ? "vanilla" : loader.toLowerCase(Locale.ROOT);
        loaderVersion = loaderVersion == null || loaderVersion.isBlank() ? "Vanilla" : loaderVersion;
        directory = directory.toAbsolutePath().normalize();
    }

    public String displayLabel() {
        return name + "  ·  " + gameVersion + " / " + loader;
    }

    @Override
    public String toString() {
        return displayLabel();
    }
}
