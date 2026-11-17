package dev.wvdoor.launcher.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstalledProfileScannerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsLauncherProfileMetadata() throws Exception {
        Path instance = temporaryDirectory.resolve("instances/standard/Test");
        Files.createDirectories(instance);
        Files.writeString(instance.resolve(".doorc-instance.json"),
                """
                {"name":"Test","gameVersion":"1.21.11","loader":"fabric","loaderVersion":"0.19.5"}
                """);

        var profiles = InstalledProfileScanner.scan(temporaryDirectory);

        assertEquals(1, profiles.size());
        assertEquals("1.21.11", profiles.get(0).gameVersion());
        assertEquals("fabric", profiles.get(0).loader());
    }

    @Test
    void detectsLoaderFromExistingProfileFiles() throws Exception {
        Path instance = temporaryDirectory.resolve("instances/DoorC-1.21.11");
        Path version = instance.resolve("versions/fabric-loader-0.19.5-1.21.11");
        Files.createDirectories(version);
        Files.writeString(version.resolve("fabric-loader-0.19.5-1.21.11.json"),
                """
                {"id":"fabric-loader-0.19.5-1.21.11","inheritsFrom":"1.21.11"}
                """);

        var profiles = InstalledProfileScanner.scan(temporaryDirectory);

        assertEquals(1, profiles.size());
        assertEquals("fabric", profiles.get(0).loader());
        assertEquals("0.19.5", profiles.get(0).loaderVersion());
        assertTrue(profiles.get(0).directory().endsWith("DoorC-1.21.11"));
    }
}
