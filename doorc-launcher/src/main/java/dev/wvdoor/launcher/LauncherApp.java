package dev.wvdoor.launcher;

import dev.wvdoor.launcher.metadata.MinecraftCatalog;
import dev.wvdoor.launcher.metadata.MinecraftCatalog.GameVersion;
import dev.wvdoor.launcher.metadata.MinecraftCatalog.Loader;
import dev.wvdoor.launcher.update.GitHubUpdateService;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.List;

public final class LauncherApp {
    private LauncherApp() {}

    public static void main(String[] args) {
        if (args.length > 0 && "--catalog-check".equals(args[0])) {
            runCatalogCheck();
            return;
        }
        if (args.length > 0 && "--update-check".equals(args[0])) {
            runUpdateCheck();
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                LauncherSettings settings = LauncherSettings.load();
                if (!settings.isConfigured() && !FirstRunSetup.show(null, settings)) return;
                new LauncherWindow(settings).show();
            } catch (IOException exception) {
                JOptionPane.showMessageDialog(null,
                        "Could not load launcher settings: " + exception.getMessage(),
                        "DoorC Launcher", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void runCatalogCheck() {
        try {
            MinecraftCatalog catalog = new MinecraftCatalog();
            List<GameVersion> versions = catalog.getGameVersions();
            long releases = versions.stream().filter(version -> !version.isSnapshot()).count();
            long snapshots = versions.size() - releases;
            System.out.println("Minecraft versions: " + versions.size()
                    + " (releases " + releases + ", snapshots/legacy " + snapshots + ")");
            for (Loader selected : List.of(Loader.FABRIC, Loader.FORGE, Loader.NEOFORGE, Loader.QUILT)) {
                List<String> available = catalog.getLoaderVersions("1.21.11", selected);
                System.out.println(selected.displayName() + " 1.21.11 versions: " + available.size());
            }
        } catch (Exception exception) {
            System.err.println("Catalog validation failed: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void runUpdateCheck() {
        try {
            GitHubUpdateService updates = new GitHubUpdateService();
            GitHubUpdateService.CheckResult result = updates.checkForUpdate();
            if (!result.hasPublishedRelease()) {
                System.out.println("No stable launcher release has been published.");
            } else if (result.update().isPresent()) {
                System.out.println("Launcher update available: " + result.update().orElseThrow().version());
            } else {
                System.out.println("Launcher is up to date: " + updates.currentVersion());
            }
        } catch (Exception exception) {
            System.err.println("Launcher update check failed: " + exception.getMessage());
            System.exit(1);
        }
    }
}
