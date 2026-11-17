package dev.wvdoor.launcher.update;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubUpdateServiceTest {
    @Test
    void comparesNumericVersionPartsInsteadOfLexicographicText() {
        assertTrue(GitHubUpdateService.isNewerVersion("1.10.0", "1.9.9"));
        assertTrue(GitHubUpdateService.isNewerVersion("2.0.0", "1.99.99"));
        assertFalse(GitHubUpdateService.isNewerVersion("1.4.2", "1.4.2"));
        assertFalse(GitHubUpdateService.isNewerVersion("1.4.1", "1.4.2"));
    }

    @Test
    void rejectsMalformedVersions() {
        assertThrows(IllegalArgumentException.class,
                () -> GitHubUpdateService.isNewerVersion("launcher-v1.4.2", "1.4.1"));
        assertThrows(IllegalArgumentException.class,
                () -> GitHubUpdateService.isNewerVersion("1.4", "1.4.1"));
    }

    @Test
    void extractsReleaseVersionFromLauncherTag() throws IOException {
        assertEquals("1.2.3", GitHubUpdateService.versionFromTag("launcher-v1.2.3"));
        assertEquals("1.2.3", GitHubUpdateService.versionFromTag("v1.2.3"));
    }

    @Test
    void checksumMustNameTheInstallerBeingVerified() throws IOException {
        String installer = "DoorCLauncher-1.0.2.exe";
        String checksum = "a".repeat(64) + "  " + installer + "\n";

        assertEquals("a".repeat(64), GitHubUpdateService.checksumFor(checksum, installer));
        assertThrows(IOException.class,
                () -> GitHubUpdateService.checksumFor(checksum, "DoorCLauncher-1.0.3.exe"));
        assertThrows(IOException.class,
                () -> GitHubUpdateService.checksumFor("not-a-hash  " + installer, installer));
    }
}
