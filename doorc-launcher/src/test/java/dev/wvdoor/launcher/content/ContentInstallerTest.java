package dev.wvdoor.launcher.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentInstallerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void extractsWorldArchivesWithoutAllowingZipSlip() throws Exception {
        Path archive = temporaryDirectory.resolve("map.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("World/level.dat"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("../outside.txt"));
            zip.write(new byte[]{4});
            zip.closeEntry();
        }
        Path destination = temporaryDirectory.resolve("extract");
        Files.createDirectory(destination);

        assertThrows(IOException.class, () -> ContentInstaller.extractArchive(archive, destination));
        assertTrue(Files.exists(destination.resolve("World/level.dat")));
        assertTrue(Files.notExists(temporaryDirectory.resolve("outside.txt")));
    }
}
