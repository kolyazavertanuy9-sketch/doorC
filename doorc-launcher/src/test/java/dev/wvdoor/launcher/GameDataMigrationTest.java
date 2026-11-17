package dev.wvdoor.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameDataMigrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsGameDataInsideTheApplicationDirectory() {
        Path install = temporaryDirectory.resolve("DoorC");

        assertTrue(GameDataMigration.isInsideInstallation(install, install));
        assertTrue(GameDataMigration.isInsideInstallation(install.resolve("instances"), install));
        assertFalse(GameDataMigration.isInsideInstallation(
                temporaryDirectory.resolve("DoorC-GameData"), install));
    }

    @Test
    void copiesGameFoldersWithoutCopyingOrDeletingTheLauncher() throws Exception {
        Path install = temporaryDirectory.resolve("DoorC");
        Path destination = temporaryDirectory.resolve("safe-data");
        Files.createDirectories(install.resolve("runtime/bin"));
        Files.createDirectories(install.resolve("instances/DoorC/saves/world"));
        Files.createDirectories(install.resolve("cache/installers"));
        Files.writeString(install.resolve("runtime/bin/java.exe"), "launcher runtime");
        Files.writeString(install.resolve("instances/DoorC/saves/world/level.dat"), "world data");
        Files.writeString(install.resolve("cache/installers/fabric.jar"), "loader cache");

        GameDataMigration.copy(install, destination, install);

        assertTrue(Files.exists(destination.resolve("instances/DoorC/saves/world/level.dat")));
        assertTrue(Files.exists(destination.resolve("cache/installers/fabric.jar")));
        assertFalse(Files.exists(destination.resolve("runtime")));
        assertTrue(Files.exists(install.resolve("runtime/bin/java.exe")));
        assertTrue(Files.exists(install.resolve("instances/DoorC/saves/world/level.dat")));
    }

    @Test
    void refusesToCopyIntoTheApplicationDirectory() throws Exception {
        Path install = temporaryDirectory.resolve("DoorC");
        Files.createDirectories(install.resolve("instances"));

        assertThrows(java.io.IOException.class,
                () -> GameDataMigration.copy(install.resolve("instances"),
                        install.resolve("backup"), install));
    }
}
