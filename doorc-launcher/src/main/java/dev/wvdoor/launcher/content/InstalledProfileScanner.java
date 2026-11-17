package dev.wvdoor.launcher.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class InstalledProfileScanner {
    private static final String PROFILE_METADATA = ".doorc-instance.json";
    private static final long MAX_METADATA_BYTES = 1024 * 1024;

    private InstalledProfileScanner() {}

    public static List<MinecraftProfile> scan(Path gameDirectory) throws IOException {
        Path root = gameDirectory.toAbsolutePath().normalize().resolve("instances");
        if (!Files.isDirectory(root)) return List.of();
        Map<String, MinecraftProfile> profiles = new LinkedHashMap<>();
        try (Stream<Path> entries = Files.list(root)) {
            for (Path entry : entries.filter(InstalledProfileScanner::isDirectory).toList()) {
                if ("standard".equalsIgnoreCase(entry.getFileName().toString())) {
                    try (Stream<Path> standard = Files.list(entry)) {
                        for (Path instance : standard.filter(InstalledProfileScanner::isDirectory).toList()) {
                            addProfiles(profiles, instance);
                        }
                    }
                } else {
                    addProfiles(profiles, entry);
                }
            }
        }
        return profiles.values().stream()
                .sorted(Comparator.comparing(MinecraftProfile::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MinecraftProfile::gameVersion)
                        .thenComparing(MinecraftProfile::loader))
                .toList();
    }

    private static void addProfiles(Map<String, MinecraftProfile> profiles, Path instance) throws IOException {
        Path metadata = instance.resolve(PROFILE_METADATA);
        if (Files.isRegularFile(metadata) && !Files.isSymbolicLink(metadata)) {
            MinecraftProfile profile = readMetadata(instance, metadata);
            profiles.put(key(profile), profile);
            return;
        }
        Path versions = instance.resolve("versions");
        if (!Files.isDirectory(versions) || Files.isSymbolicLink(versions)) return;
        try (Stream<Path> versionDirectories = Files.list(versions)) {
            for (Path versionDirectory : versionDirectories.filter(InstalledProfileScanner::isDirectory).toList()) {
                Path versionFile = versionDirectory.resolve(versionDirectory.getFileName() + ".json");
                if (!Files.isRegularFile(versionFile) || Files.isSymbolicLink(versionFile)
                        || Files.size(versionFile) > MAX_METADATA_BYTES) continue;
                MinecraftProfile profile = readVersionMetadata(instance, versionFile);
                if (profile != null) profiles.putIfAbsent(key(profile), profile);
            }
        }
    }

    private static MinecraftProfile readMetadata(Path instance, Path metadata) throws IOException {
        if (Files.size(metadata) > MAX_METADATA_BYTES) throw new IOException("Instance metadata file is too large.");
        JsonObject json = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
        return new MinecraftProfile(
                string(json, "name", instance.getFileName().toString()),
                instance,
                string(json, "gameVersion", ""),
                string(json, "loader", "vanilla"),
                string(json, "loaderVersion", "Vanilla"));
    }

    private static MinecraftProfile readVersionMetadata(Path instance, Path versionFile) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(versionFile)).getAsJsonObject();
        String id = string(json, "id", versionFile.getParent().getFileName().toString());
        String gameVersion = json.has("inheritsFrom")
                ? json.get("inheritsFrom").getAsString() : id;
        String lowerId = id.toLowerCase(Locale.ROOT);
        LoaderIdentity loader = loaderFromId(lowerId, gameVersion);
        if (loader == null) {
            if (json.has("inheritsFrom")) return null;
            loader = new LoaderIdentity("vanilla", "Vanilla");
        }
        return new MinecraftProfile(instance.getFileName().toString(), instance,
                gameVersion, loader.name(), loader.version());
    }

    private static LoaderIdentity loaderFromId(String id, String gameVersion) {
        for (String loader : List.of("fabric", "forge", "neoforge", "quilt")) {
            String prefix = loader + "-loader-";
            if (id.startsWith(prefix)) {
                String version = id.substring(prefix.length());
                String suffix = "-" + gameVersion.toLowerCase(Locale.ROOT);
                if (version.endsWith(suffix)) {
                    version = version.substring(0, version.length() - suffix.length());
                }
                return new LoaderIdentity(loader, version);
            }
        }
        for (String loader : List.of("forge", "neoforge")) {
            String marker = "-" + loader + "-";
            int position = id.indexOf(marker);
            if (position > 0) {
                String version = id.substring(position + marker.length());
                String suffix = "-" + gameVersion.toLowerCase(Locale.ROOT);
                if (version.endsWith(suffix)) {
                    version = version.substring(0, version.length() - suffix.length());
                }
                return new LoaderIdentity(loader, version);
            }
        }
        return null;
    }

    private static String key(MinecraftProfile profile) {
        return profile.directory() + "|" + profile.gameVersion() + "|" + profile.loader();
    }

    private static boolean isDirectory(Path path) {
        return Files.isDirectory(path) && !Files.isSymbolicLink(path);
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : fallback;
    }

    private record LoaderIdentity(String name, String version) {}
}
