package dev.crystalutility.resourcepack;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ResourcePackAutoLoader {
    private static final Logger LOGGER = Logger.getLogger("wvDoorClient");
    private static final List<String> PACKS = List.of(
            "wv-low-on-fire-v26.2.zip",
            "wv-side-shield.zip",
            "wv-small-totem.zip"
    );
    private static final String REMOVED_PACK = "wv-klasic-romance.zip";

    private ResourcePackAutoLoader() {}

    public static void activate(Minecraft client) {
        Path resourcePackDirectory = client.getResourcePackDirectory();
        for (String fileName : PACKS) {
            installPack(resourcePackDirectory, fileName);
        }

        PackRepository repository = client.getResourcePackRepository();
        repository.reload();

        LinkedHashSet<String> selectedIds = new LinkedHashSet<>(repository.getSelectedIds());
        selectedIds.remove("file/" + REMOVED_PACK);
        for (String fileName : PACKS) {
            String packId = "file/" + fileName;
            if (repository.isAvailable(packId)) {
                selectedIds.add(packId);
            } else {
                LOGGER.severe("Bundled resource pack was not discovered: " + packId);
            }
        }

        repository.setSelected(selectedIds);
        client.options.updateResourcePacks(repository);
        client.options.save();
        client.reloadResourcePacks().whenComplete((ignored, error) -> {
            client.execute(() -> finishActivation(client, resourcePackDirectory, error));
        });
    }

    private static void finishActivation(Minecraft client, Path resourcePackDirectory, Throwable error) {
        removeOldPack(resourcePackDirectory);
        client.getResourcePackRepository().reload();
        if (error != null) {
            LOGGER.log(Level.SEVERE, "Could not activate the bundled resource packs", error);
        } else {
            LOGGER.info("Activated the three bundled wvDoorClient resource packs.");
        }
    }

    private static void removeOldPack(Path resourcePackDirectory) {
        try {
            Files.deleteIfExists(resourcePackDirectory.resolve(REMOVED_PACK));
        } catch (IOException exception) {
            LOGGER.log(Level.SEVERE, "Could not remove bundled resource pack " + REMOVED_PACK, exception);
        }
    }

    private static void installPack(Path resourcePackDirectory, String fileName) {
        String resourcePath = "/wvdoorclient/resourcepacks/" + fileName;
        Path destination = resourcePackDirectory.resolve(fileName);
        try (InputStream input = ResourcePackAutoLoader.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                LOGGER.severe("Bundled resource pack is missing from the mod: " + resourcePath);
                return;
            }
            Files.createDirectories(resourcePackDirectory);
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            LOGGER.log(Level.SEVERE, "Could not install bundled resource pack to " + destination, exception);
        }
    }
}
