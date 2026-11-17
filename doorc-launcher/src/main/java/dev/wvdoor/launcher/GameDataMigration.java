package dev.wvdoor.launcher;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public final class GameDataMigration {
    private static final List<String> GAME_DATA_AT_INSTALL_ROOT = List.of(
            "instances", "cache", "versions", "assets", "libraries", "natives",
            "mods", "config", "saves", "screenshots", "resourcepacks", "shaderpacks",
            "logs", "crash-reports", "options.txt", "servers.dat");

    private GameDataMigration() {}

    public static boolean isInsideInstallation(Path gameDirectory, Path installationDirectory) {
        return normalized(gameDirectory).startsWith(normalized(installationDirectory));
    }

    public static Path createDestination(Path installationDirectory) throws IOException {
        Path install = normalized(installationDirectory);
        for (Path parent : candidateParents()) {
            Path candidate = parent.resolve("DoorC-GameData-" + UUID.randomUUID().toString().substring(0, 8))
                    .toAbsolutePath().normalize();
            if (!candidate.startsWith(install) && !install.startsWith(candidate)
                    && !Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IOException("Could not find a game-data folder outside the launcher installation folder.");
    }

    public static void copy(Path gameDirectory, Path destination, Path installationDirectory)
            throws IOException {
        Path source = normalized(gameDirectory);
        Path target = normalized(destination);
        Path install = normalized(installationDirectory);
        if (!source.startsWith(install)) {
            throw new IOException("The current game-data folder is not inside the launcher installation folder.");
        }
        if (target.startsWith(install) || install.startsWith(target) || Files.exists(target)) {
            throw new IOException("The new game-data folder is not a safe, empty destination.");
        }
        if (!Files.isDirectory(source) || Files.isSymbolicLink(source)) {
            throw new IOException("The current game-data folder is unavailable or is a symbolic link.");
        }

        Files.createDirectories(target.getParent());
        Files.createDirectory(target);
        if (source.equals(install)) {
            for (String name : GAME_DATA_AT_INSTALL_ROOT) {
                Path entry = source.resolve(name);
                if (Files.exists(entry)) copyPath(entry, target.resolve(name));
            }
        } else {
            try (Stream<Path> entries = Files.list(source)) {
                for (Path entry : entries.toList()) {
                    copyPath(entry, target.resolve(entry.getFileName()));
                }
            }
        }
    }

    private static void copyPath(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source)) {
            throw new IOException("Game data contains a symbolic link that cannot be safely migrated: " + source);
        }
        if (!Files.isDirectory(source)) {
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            return;
        }

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException("Game data contains a symbolic link that cannot be safely migrated: "
                            + directory);
                }
                Files.createDirectories(target.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink()) {
                    throw new IOException("Game data contains a symbolic link that cannot be safely migrated: "
                            + file);
                }
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static List<Path> candidateParents() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path local = localAppData == null || localAppData.isBlank()
                ? LauncherSettings.appDataDirectory() : Path.of(localAppData);
        return List.of(local, LauncherSettings.appDataDirectory(),
                Path.of(System.getProperty("user.home")).resolve("Documents"),
                Path.of(System.getProperty("user.home")));
    }

    private static Path normalized(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
