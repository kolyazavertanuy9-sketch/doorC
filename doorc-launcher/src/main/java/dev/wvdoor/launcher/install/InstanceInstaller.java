package dev.wvdoor.launcher.install;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.wvdoor.launcher.metadata.MinecraftCatalog;
import dev.wvdoor.launcher.metadata.MinecraftCatalog.GameVersion;
import dev.wvdoor.launcher.metadata.MinecraftCatalog.Loader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class InstanceInstaller {
    private static final String DOORC_GAME_VERSION = "1.21.11";
    private static final int DOWNLOAD_THREADS = 8;
    private final MinecraftCatalog catalog;
    private final Supplier<Path> gameRoot;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public InstanceInstaller(MinecraftCatalog catalog, Supplier<Path> gameRoot) {
        this.catalog = catalog;
        this.gameRoot = gameRoot;
    }

    public void install(GameVersion version, Loader loader, String loaderVersion,
                        Path gameDirectory, ProgressListener progress) throws Exception {
        Files.createDirectories(gameDirectory);
        progress.update("Downloading Minecraft " + version.id() + " metadata...", 0, 0);
        installVanillaFiles(version, gameDirectory, progress);
        if (loader != Loader.VANILLA) {
            progress.update("Installing " + loader.displayName() + " " + loaderVersion + "...", 0, 0);
            runLoaderInstaller(version.id(), loader, loaderVersion, gameDirectory);
        }
        writeInstanceMetadata(version, loader, loaderVersion, gameDirectory);
    }

    public void installDoorC(Path gameDirectory, ProgressListener progress) throws Exception {
        Files.createDirectories(gameDirectory);
        GameVersion version = catalog.getGameVersions().stream()
                .filter(candidate -> candidate.id().equals(DOORC_GAME_VERSION))
                .findFirst()
                .orElseThrow(() -> new IOException("Minecraft " + DOORC_GAME_VERSION + " is not in the official catalog."));

        List<String> fabricVersions = catalog.getLoaderVersions(DOORC_GAME_VERSION, Loader.FABRIC);
        if (fabricVersions.isEmpty()) throw new IOException("No Fabric loader is available for DoorC.");
        install(version, Loader.FABRIC, fabricVersions.get(0), gameDirectory, progress);
        Path mods = gameDirectory.resolve("mods");
        Files.createDirectories(mods);
        try (InputStream clientMod = requiredPayload("/mods/wvDoorClient.jar");
             InputStream libraryMod = requiredPayload("/mods/WVlib.jar")) {
            Files.copy(clientMod, mods.resolve("wvDoorClient.jar"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.copy(libraryMod, mods.resolve("WVlib.jar"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        progress.update("DoorC mod and WVlib copied into its private mods folder.", 1, 1);
    }

    private void installVanillaFiles(GameVersion version, Path gameDirectory,
                                     ProgressListener progress) throws Exception {
        byte[] versionJson = getBytes(version.url());
        JsonObject details = JsonParser.parseString(new String(versionJson, java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject();

        Path versionsDirectory = gameDirectory.resolve("versions").resolve(version.id());
        Files.createDirectories(versionsDirectory);
        writeIfChanged(versionsDirectory.resolve(version.id() + ".json"), versionJson);

        JsonObject client = details.getAsJsonObject("downloads").getAsJsonObject("client");
        download(client, versionsDirectory.resolve(version.id() + ".jar"));

        Path librariesRoot = gameDirectory.resolve("libraries");
        Path nativesRoot = gameDirectory.resolve("natives").resolve(version.id());
        Files.createDirectories(nativesRoot);
        List<Callable<Void>> libraryDownloads = new ArrayList<>();
        Set<Path> scheduledLibraries = new HashSet<>();
        JsonArray libraries = details.getAsJsonArray("libraries");
        for (JsonElement element : libraries) {
            JsonObject library = element.getAsJsonObject();
            if (!libraryAllowed(library)) continue;
            JsonObject downloads = library.has("downloads") && library.get("downloads").isJsonObject()
                    ? library.getAsJsonObject("downloads") : null;

            if (downloads != null && downloads.has("artifact")) {
                JsonObject artifact = downloads.getAsJsonObject("artifact");
                Path target = librariesRoot.resolve(artifact.get("path").getAsString());
                if (scheduledLibraries.add(target)) {
                    libraryDownloads.add(() -> {
                        download(artifact, target);
                        return null;
                    });
                }
            }
            if (library.has("natives")) {
                String classifier = nativeClassifier(library);
                JsonObject nativeArtifact = null;
                String nativeUrl = null;
                if (classifier != null && downloads != null && downloads.has("classifiers")
                        && downloads.getAsJsonObject("classifiers").has(classifier)) {
                    nativeArtifact = downloads.getAsJsonObject("classifiers").getAsJsonObject(classifier);
                    nativeUrl = nativeArtifact.get("url").getAsString();
                } else if (classifier != null && downloads == null) {
                    LegacyArtifact legacy = legacyArtifact(library, classifier);
                    nativeArtifact = legacy.metadata();
                    nativeUrl = legacy.url();
                }
                if (nativeArtifact != null) {
                    JsonObject artifact = nativeArtifact;
                    String artifactUrl = nativeUrl;
                    Path target = librariesRoot.resolve(artifact.get("path").getAsString());
                    if (!scheduledLibraries.add(target)) continue;
                    libraryDownloads.add(() -> {
                        download(artifact, target, artifactUrl);
                        extractNative(target, nativesRoot);
                        return null;
                    });
                }
            }
            if (downloads == null && !library.has("natives")) {
                LegacyArtifact legacy = legacyArtifact(library, null);
                Path target = librariesRoot.resolve(legacy.metadata().get("path").getAsString());
                if (scheduledLibraries.add(target)) {
                    libraryDownloads.add(() -> {
                        download(legacy.metadata(), target, legacy.url());
                        return null;
                    });
                }
            }
        }
        runDownloads(libraryDownloads, progress, "Downloading game libraries");

        if (details.has("assetIndex")) {
            JsonObject index = details.getAsJsonObject("assetIndex");
            Path indexPath = gameDirectory.resolve("assets/indexes")
                    .resolve(index.get("id").getAsString() + ".json");
            download(index, indexPath);
            JsonObject assetIndex = JsonParser.parseString(Files.readString(indexPath))
                    .getAsJsonObject();
            JsonObject objects = assetIndex.getAsJsonObject("objects");
            List<Callable<Void>> assetDownloads = new ArrayList<>(objects.size());
            Set<Path> scheduledAssets = new HashSet<>();
            for (var entry : objects.entrySet()) {
                JsonObject asset = entry.getValue().getAsJsonObject();
                String hash = asset.get("hash").getAsString();
                Path target = gameDirectory.resolve("assets/objects")
                        .resolve(hash.substring(0, 2)).resolve(hash);
                if (!scheduledAssets.add(target)) continue;
                assetDownloads.add(() -> {
                    download(asset, target,
                            "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash);
                    return null;
                });
            }
            runDownloads(assetDownloads, progress, "Downloading Minecraft assets");
        }

        if (details.has("logging") && details.getAsJsonObject("logging").has("client")) {
            JsonObject clientLog = details.getAsJsonObject("logging").getAsJsonObject("client");
            JsonObject file = clientLog.getAsJsonObject("file");
            download(file, gameDirectory.resolve("assets/log_configs")
                    .resolve(file.get("id").getAsString()));
        }
    }

    private void runLoaderInstaller(String gameVersion, Loader loader, String loaderVersion,
                                    Path gameDirectory) throws Exception {
        String url = switch (loader) {
            case VANILLA -> throw new IllegalArgumentException("Vanilla does not require an installer.");
            case FABRIC -> catalog.getFabricInstallerUrl();
            case QUILT -> catalog.getQuiltInstallerUrl();
            case FORGE -> catalog.getForgeInstallerUrl(loaderVersion);
            case NEOFORGE -> catalog.getNeoForgeInstallerUrl(loaderVersion);
        };
        Path cache = gameRoot.get().resolve("cache/installers");
        Files.createDirectories(cache);
        String fileName = loader.displayName().toLowerCase(Locale.ROOT) + "-"
                + loaderVersion.replaceAll("[^A-Za-z0-9._-]", "_") + "-installer.jar";
        Path installer = cache.resolve(fileName);
        download(url, installer, null, -1);

        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-jar");
        command.add(installer.toString());
        switch (loader) {
            case FABRIC -> {
                command.add("client");
                command.add("-dir");
                command.add(gameDirectory.toString());
                command.add("-mcversion");
                command.add(gameVersion);
                command.add("-loader");
                command.add(loaderVersion);
                command.add("-noprofile");
            }
            case QUILT -> {
                command.add("install");
                command.add("client");
                command.add(gameVersion);
                command.add(loaderVersion);
                command.add("--install-dir=" + gameDirectory);
                command.add("--no-profile");
            }
            case FORGE, NEOFORGE -> {
                command.add("--installClient");
                command.add(gameDirectory.toString());
            }
            case VANILLA -> throw new IllegalArgumentException("Vanilla does not require an installer.");
        }

        Process process = new ProcessBuilder(command)
                .directory(gameDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        StringBuilder output = new StringBuilder();
        try (var reader = process.inputReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 8000) output.delete(0, output.length() - 4000);
                output.append(line).append(System.lineSeparator());
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(loader.displayName() + " installer exited with " + exitCode
                    + (output.isEmpty() ? "." : ": " + output));
        }
    }

    private void runDownloads(List<Callable<Void>> downloads, ProgressListener progress,
                              String label) throws Exception {
        if (downloads.isEmpty()) return;
        ExecutorService executor = Executors.newFixedThreadPool(DOWNLOAD_THREADS);
        AtomicInteger completed = new AtomicInteger();
        try {
            List<Future<Void>> futures = new ArrayList<>(downloads.size());
            for (Callable<Void> download : downloads) {
                futures.add(executor.submit(() -> {
                    download.call();
                    int count = completed.incrementAndGet();
                    if (count == 1 || count == downloads.size() || count % 25 == 0) {
                        progress.update(label + "...", count, downloads.size());
                    }
                    return null;
                }));
            }
            for (Future<Void> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
    }

    private void download(JsonObject metadata, Path target) throws IOException, InterruptedException {
        download(metadata, target, metadata.get("url").getAsString(),
                metadata.has("size") ? metadata.get("size").getAsLong() : -1);
    }

    private void download(JsonObject metadata, Path target, String url) throws IOException, InterruptedException {
        download(metadata, target, url, metadata.has("size") ? metadata.get("size").getAsLong() : -1);
    }

    private void download(JsonObject metadata, Path target, String url, long expectedSize)
            throws IOException, InterruptedException {
        String expectedSha1 = metadata.has("sha1") ? metadata.get("sha1").getAsString() : null;
        if (Files.isRegularFile(target)
                && (expectedSize < 0 || Files.size(target) == expectedSize)
                && (expectedSha1 == null || sha1(target).equalsIgnoreCase(expectedSha1))) {
            return;
        }
        download(url, target, expectedSha1, expectedSize);
    }

    private void download(String url, Path target, String expectedSha1, long expectedSize)
            throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "DoorCLauncher/1.0")
                .GET()
                .build();
        HttpResponse<Path> response = http.send(request,
                HttpResponse.BodyHandlers.ofFile(temporary));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(temporary);
            throw new IOException("Download failed with HTTP " + response.statusCode()
                    + " from " + URI.create(url).getHost());
        }
        if (expectedSize >= 0 && Files.size(temporary) != expectedSize) {
            Files.deleteIfExists(temporary);
            throw new IOException("Downloaded file size did not match metadata: " + url);
        }
        if (expectedSha1 != null && !sha1(temporary).equalsIgnoreCase(expectedSha1)) {
            Files.deleteIfExists(temporary);
            throw new IOException("Downloaded file SHA-1 did not match metadata: " + url);
        }
        moveReplace(temporary, target);
    }

    private byte[] getBytes(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "DoorCLauncher/1.0")
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Request failed with HTTP " + response.statusCode()
                    + " from " + URI.create(url).getHost());
        }
        return response.body();
    }

    private static boolean libraryAllowed(JsonObject library) {
        if (!library.has("rules")) return true;
        boolean allowed = false;
        for (JsonElement ruleElement : library.getAsJsonArray("rules")) {
            JsonObject rule = ruleElement.getAsJsonObject();
            if (!ruleMatchesWindows(rule)) continue;
            allowed = "allow".equals(rule.get("action").getAsString());
        }
        return allowed;
    }

    private static boolean ruleMatchesWindows(JsonObject rule) {
        if (!rule.has("os")) return true;
        JsonObject os = rule.getAsJsonObject("os");
        if (os.has("name") && !"windows".equals(os.get("name").getAsString())) return false;
        if (os.has("arch")) {
            String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
            if (!arch.matches(os.get("arch").getAsString())) return false;
        }
        if (os.has("version")) {
            String version = System.getProperty("os.version");
            if (!version.matches(os.get("version").getAsString())) return false;
        }
        return !rule.has("features") || rule.getAsJsonObject("features").isEmpty();
    }

    private static String nativeClassifier(JsonObject library) {
        JsonObject natives = library.getAsJsonObject("natives");
        if (!natives.has("windows")) return null;
        String classifier = natives.get("windows").getAsString();
        if (library.has("extract") && library.getAsJsonObject("extract").has("exclude")) {
            return classifier.replace("${arch}", System.getProperty("os.arch").contains("64") ? "64" : "32");
        }
        return classifier.replace("${arch}", System.getProperty("os.arch").contains("64") ? "64" : "32");
    }

    private static LegacyArtifact legacyArtifact(JsonObject library, String classifier) throws IOException {
        String[] coordinate = library.get("name").getAsString().split(":");
        if (coordinate.length < 3) throw new IOException("Invalid legacy library coordinate.");
        String groupPath = coordinate[0].replace('.', '/');
        String artifact = coordinate[1];
        String version = coordinate[2];
        String suffix = classifier == null
                ? (coordinate.length > 3 ? "-" + coordinate[3] : "")
                : "-" + classifier;
        String fileName = artifact + "-" + version + suffix + ".jar";
        String path = groupPath + "/" + artifact + "/" + version + "/" + fileName;
        String repository = library.has("url")
                ? library.get("url").getAsString() : "https://libraries.minecraft.net/";
        if (!repository.endsWith("/")) repository += "/";
        JsonObject metadata = new JsonObject();
        metadata.addProperty("path", path);
        return new LegacyArtifact(repository + path, metadata);
    }

    private static void extractNative(Path archive, Path targetDirectory) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) continue;
                Path target = targetDirectory.resolve(entry.getName()).normalize();
                if (!target.startsWith(targetDirectory)) {
                    throw new IOException("Native archive contains an invalid path.");
                }
                Files.createDirectories(target.getParent());
                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void writeIfChanged(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        if (Files.isRegularFile(target) && java.util.Arrays.equals(Files.readAllBytes(target), bytes)) return;
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        Files.write(temporary, bytes);
        moveReplace(temporary, target);
    }

    private static void writeInstanceMetadata(GameVersion version, Loader loader, String loaderVersion,
                                              Path gameDirectory) throws IOException {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("name", gameDirectory.getFileName().toString());
        metadata.addProperty("gameVersion", version.id());
        metadata.addProperty("loader", loader.name().toLowerCase(Locale.ROOT));
        metadata.addProperty("loaderVersion", loader == Loader.VANILLA ? "Vanilla" : loaderVersion);
        writeIfChanged(gameDirectory.resolve(".doorc-instance.json"),
                metadata.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha1(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is not available in this Java runtime.", exception);
        }
    }

    private InputStream requiredPayload(String resource) throws IOException {
        InputStream input = InstanceInstaller.class.getResourceAsStream(resource);
        if (input == null) throw new IOException("Bundled DoorC mod is missing: " + resource);
        return input;
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private record LegacyArtifact(String url, JsonObject metadata) {}

    @FunctionalInterface
    public interface ProgressListener {
        void update(String message, int completed, int total);
    }
}
