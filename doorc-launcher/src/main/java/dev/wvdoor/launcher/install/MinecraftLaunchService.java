package dev.wvdoor.launcher.install;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.wvdoor.launcher.auth.GameAccount;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

public final class MinecraftLaunchService {
    private static final String DOORC_GAME_VERSION = "1.21.11";
    private static final URI AUTHLIB_RELEASE_API =
            URI.create("https://api.github.com/repos/yushijinhun/authlib-injector/releases/latest");
    private static final long MAX_AUTHLIB_SIZE = 10L * 1024 * 1024;
    private static final Map<String, Boolean> FEATURES = Map.of(
            "is_demo_user", false,
            "has_custom_resolution", false,
            "has_quick_plays_support", false,
            "is_quick_play_singleplayer", false,
            "is_quick_play_multiplayer", false,
            "is_quick_play_realms", false);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Process launchDoorC(Path gameDirectory, GameAccount account) throws Exception {
        Path instanceDirectory = gameDirectory.resolve("instances").resolve("DoorC-1.21.11");
        Path versionsDirectory = instanceDirectory.resolve("versions");
        JsonObject profile = findDoorCProfile(versionsDirectory);
        String versionId = profile.get("id").getAsString();
        String inheritedId = profile.get("inheritsFrom").getAsString();
        Path baseJson = versionsDirectory.resolve(inheritedId).resolve(inheritedId + ".json");
        JsonObject base = readJson(baseJson);
        Path clientJar = versionsDirectory.resolve(inheritedId).resolve(inheritedId + ".jar");
        if (!Files.isRegularFile(clientJar)) {
            throw new IOException("Minecraft client files are missing. Reinstall DoorC first.");
        }

        String assetIndex = base.has("assetIndex")
                ? base.getAsJsonObject("assetIndex").get("id").getAsString() : "";
        if (assetIndex.isBlank()) throw new IOException("Minecraft asset index is missing.");

        Path nativesDirectory = instanceDirectory.resolve("natives").resolve(inheritedId);
        if (!Files.isDirectory(nativesDirectory)) {
            throw new IOException("Minecraft native libraries are missing. Reinstall DoorC first.");
        }
        String classpath = buildClasspath(instanceDirectory, base, profile, clientJar);
        Map<String, String> substitutions = buildSubstitutions(gameDirectory, instanceDirectory,
                nativesDirectory, versionId, assetIndex, account, classpath);

        Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        if (!Files.isRegularFile(java)) {
            throw new IOException("The bundled Java runtime is missing java.exe.");
        }

        List<String> jvmArguments = inheritedArguments(base, profile, "jvm");
        if (account.provider() == GameAccount.Provider.ELY_BY) {
            jvmArguments.add(0, "-javaagent:" + ensureAuthlibInjector(gameDirectory) + "=ely.by");
        }
        for (int i = 0; i < jvmArguments.size(); i++) {
            jvmArguments.set(i, substitute(jvmArguments.get(i), substitutions));
        }
        if (jvmArguments.stream().noneMatch(argument -> argument.startsWith("-Xmx"))) {
            jvmArguments.add("-Xmx2G");
        }
        if (jvmArguments.stream().noneMatch(argument -> argument.startsWith("-Djava.library.path="))) {
            jvmArguments.add("-Djava.library.path=" + nativesDirectory);
        }
        if (!hasClasspathOption(jvmArguments)) {
            jvmArguments.add("-cp");
            jvmArguments.add(classpath);
        }

        String mainClass = profile.has("mainClass")
                ? profile.get("mainClass").getAsString()
                : base.has("mainClass") ? base.get("mainClass").getAsString() : "";
        if (mainClass.isBlank()) throw new IOException("Minecraft launch metadata has no main class.");

        List<String> command = new ArrayList<>();
        command.add(java.toString());
        command.addAll(jvmArguments);
        command.add(mainClass);
        List<String> gameArguments = inheritedArguments(base, profile, "game");
        if (gameArguments.isEmpty()) throw new IOException("Minecraft launch arguments are missing.");
        for (String argument : gameArguments) command.add(substitute(argument, substitutions));

        Path logs = instanceDirectory.resolve("logs");
        Files.createDirectories(logs);
        return new ProcessBuilder(command)
                .directory(instanceDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(logs.resolve("doorc-launcher.log").toFile())
                .start();
    }

    private JsonObject findDoorCProfile(Path versionsDirectory) throws IOException {
        if (!Files.isDirectory(versionsDirectory)) {
            throw new IOException("DoorC is not installed yet.");
        }
        try (var directories = Files.list(versionsDirectory)) {
            List<Path> candidates = directories
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparingLong(MinecraftLaunchService::lastModified).reversed())
                    .toList();
            for (Path directory : candidates) {
                Path json = directory.resolve(directory.getFileName() + ".json");
                if (!Files.isRegularFile(json)) continue;
                JsonObject version = readJson(json);
                if (version.has("id") && version.has("inheritsFrom")
                        && version.get("inheritsFrom").getAsString().equals(DOORC_GAME_VERSION)
                        && version.get("id").getAsString().startsWith("fabric-loader-")) {
                    return version;
                }
            }
        }
        throw new IOException("DoorC's Fabric profile was not found. Reinstall DoorC first.");
    }

    private String buildClasspath(Path gameDirectory, JsonObject base, JsonObject profile,
                                  Path clientJar) throws IOException {
        Path librariesRoot = gameDirectory.resolve("libraries").toAbsolutePath().normalize();
        Set<Path> libraries = new LinkedHashSet<>();
        addLibraries(base, librariesRoot, libraries);
        addLibraries(profile, librariesRoot, libraries);
        List<String> entries = new ArrayList<>();
        for (Path library : libraries) {
            if (!Files.isRegularFile(library)) {
                throw new IOException("A required Minecraft library is missing: " + library.getFileName());
            }
            entries.add(library.toString());
        }
        entries.add(clientJar.toAbsolutePath().normalize().toString());
        return String.join(java.io.File.pathSeparator, entries);
    }

    private static void addLibraries(JsonObject version, Path librariesRoot, Set<Path> target)
            throws IOException {
        if (!version.has("libraries")) return;
        for (JsonElement element : version.getAsJsonArray("libraries")) {
            JsonObject library = element.getAsJsonObject();
            if (!rulesAllow(library)) continue;
            String relativePath = null;
            if (library.has("downloads") && library.getAsJsonObject("downloads").has("artifact")) {
                relativePath = library.getAsJsonObject("downloads").getAsJsonObject("artifact")
                        .get("path").getAsString();
            } else if (library.has("name")) {
                relativePath = mavenPath(library.get("name").getAsString());
            }
            if (relativePath == null) continue;
            Path file = librariesRoot.resolve(relativePath).normalize();
            if (!file.startsWith(librariesRoot)) {
                throw new IOException("Minecraft metadata contains an invalid library path.");
            }
            target.add(file);
        }
    }

    private Path ensureAuthlibInjector(Path gameDirectory) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(AUTHLIB_RELEASE_API)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DoorCLauncher")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Could not check the official authlib-injector release (HTTP "
                    + response.statusCode() + ").");
        }
        JsonObject release = parseJson(response.body(), "authlib-injector release metadata");
        String tag = release.get("tag_name").getAsString();
        JsonObject asset = findAuthlibAsset(release);
        String assetName = asset.get("name").getAsString();
        if (!tag.matches("v[0-9]+(?:\\.[0-9]+){1,3}")
                || !assetName.equals("authlib-injector-" + tag.substring(1) + ".jar")) {
            throw new IOException("The official authlib-injector release version is invalid.");
        }
        String digest = asset.has("digest") ? asset.get("digest").getAsString() : "";
        if (!digest.matches("sha256:[0-9a-fA-F]{64}")) {
            throw new IOException("The official authlib-injector release has no SHA-256 digest.");
        }
        long expectedSize = asset.get("size").getAsLong();
        if (expectedSize <= 0 || expectedSize > MAX_AUTHLIB_SIZE) {
            throw new IOException("The official authlib-injector asset has an unexpected size.");
        }
        String downloadUrl = asset.get("browser_download_url").getAsString();
        if (!downloadUrl.startsWith("https://github.com/yushijinhun/authlib-injector/releases/download/"
                + tag + "/")) {
            throw new IOException("The authlib-injector download URL is not trusted.");
        }

        Path cache = gameDirectory.resolve("cache");
        Files.createDirectories(cache);
        Path jar = cache.resolve(assetName);
        String expectedSha256 = digest.substring("sha256:".length());
        if (Files.isRegularFile(jar) && sha256(jar).equalsIgnoreCase(expectedSha256)
                && hasPremainClass(jar)) {
            return jar;
        }

        HttpRequest downloadRequest = HttpRequest.newBuilder(URI.create(downloadUrl))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "DoorCLauncher")
                .GET()
                .build();
        HttpResponse<InputStream> download = http.send(downloadRequest,
                HttpResponse.BodyHandlers.ofInputStream());
        if (download.statusCode() != 200) {
            download.body().close();
            throw new IOException("authlib-injector download failed with HTTP " + download.statusCode() + ".");
        }
        Path temporary = jar.resolveSibling(jar.getFileName() + ".part");
        try {
            try (InputStream input = download.body();
                 ByteArrayOutputStream output = new ByteArrayOutputStream((int) expectedSize)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    if (output.size() > MAX_AUTHLIB_SIZE) {
                        throw new IOException("authlib-injector download exceeded the size limit.");
                    }
                }
                byte[] bytes = output.toByteArray();
                if (bytes.length != expectedSize) {
                    throw new IOException("authlib-injector download size did not match GitHub metadata.");
                }
                if (!sha256(bytes).equalsIgnoreCase(expectedSha256)) {
                    throw new IOException("authlib-injector failed its SHA-256 verification.");
                }
                Files.write(temporary, bytes);
            }
            if (!hasPremainClass(temporary)) {
                throw new IOException("The authlib-injector release is not a valid Java agent.");
            }
            try {
                Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
        return jar;
    }

    private static JsonObject findAuthlibAsset(JsonObject release) throws IOException {
        if (!release.has("assets")) throw new IOException("The authlib-injector release has no assets.");
        for (JsonElement element : release.getAsJsonArray("assets")) {
            JsonObject asset = element.getAsJsonObject();
            if (asset.has("name") && asset.get("name").getAsString()
                    .matches("authlib-injector-[A-Za-z0-9._-]+\\.jar")) {
                return asset;
            }
        }
        throw new IOException("The official authlib-injector release JAR was not found.");
    }

    private static boolean hasPremainClass(Path jar) throws IOException {
        try (ZipFile archive = new ZipFile(jar.toFile())) {
            var manifestEntry = archive.getEntry("META-INF/MANIFEST.MF");
            if (manifestEntry == null) return false;
            try (InputStream input = archive.getInputStream(manifestEntry)) {
                java.util.jar.Manifest manifest = new java.util.jar.Manifest(input);
                return manifest.getMainAttributes().getValue("Premain-Class") != null;
            }
        }
    }

    private static Map<String, String> buildSubstitutions(Path gameDirectory, Path instanceDirectory,
                                                           Path nativesDirectory, String versionId,
                                                           String assetIndex, GameAccount account,
                                                           String classpath) {
        Map<String, String> substitutions = new LinkedHashMap<>();
        substitutions.put("${auth_player_name}", account.playerName());
        substitutions.put("${auth_uuid}", account.uuid());
        substitutions.put("${auth_access_token}", account.accessToken());
        substitutions.put("${auth_session}", "token:" + account.accessToken() + ":" + account.uuid());
        substitutions.put("${user_type}", account.userType());
        substitutions.put("${user_properties}", "{}");
        substitutions.put("${auth_xuid}", "");
        substitutions.put("${clientid}", "00000000-0000-0000-0000-000000000000");
        substitutions.put("${version_name}", versionId);
        substitutions.put("${version_type}", "release");
        substitutions.put("${game_directory}", instanceDirectory.toAbsolutePath().toString());
        substitutions.put("${assets_root}", gameDirectory.resolve("assets").toAbsolutePath().toString());
        substitutions.put("${assets_index_name}", assetIndex);
        substitutions.put("${natives_directory}", nativesDirectory.toAbsolutePath().toString());
        substitutions.put("${library_directory}", gameDirectory.resolve("libraries").toAbsolutePath().toString());
        substitutions.put("${classpath}", classpath);
        substitutions.put("${classpath_separator}", java.io.File.pathSeparator);
        substitutions.put("${launcher_name}", "DoorCLauncher");
        substitutions.put("${launcher_version}", System.getProperty("doorc.launcher.version", "1.0.3"));
        substitutions.put("${resolution_width}", "854");
        substitutions.put("${resolution_height}", "480");
        substitutions.put("${quickPlayPath}", "");
        substitutions.put("${quickPlaySingleplayer}", "");
        substitutions.put("${quickPlayMultiplayer}", "");
        substitutions.put("${quickPlayRealms}", "");
        return substitutions;
    }

    private static List<String> inheritedArguments(JsonObject parent, JsonObject child, String category)
            throws IOException {
        List<String> arguments = new ArrayList<>(readArguments(parent, category));
        arguments.addAll(readArguments(child, category));
        return arguments;
    }

    private static List<String> readArguments(JsonObject version, String category) throws IOException {
        if (version.has("arguments") && version.getAsJsonObject("arguments").has(category)) {
            List<String> result = new ArrayList<>();
            for (JsonElement element : version.getAsJsonObject("arguments").getAsJsonArray(category)) {
                if (element.isJsonPrimitive()) {
                    result.add(element.getAsString());
                } else if (element.isJsonObject()) {
                    JsonObject argument = element.getAsJsonObject();
                    if (!rulesAllow(argument)) continue;
                    addArgumentValue(result, argument.get("value"));
                }
            }
            return result;
        }
        if ("game".equals(category) && version.has("minecraftArguments")) {
            return List.of(version.get("minecraftArguments").getAsString().trim().split("\\s+"));
        }
        return List.of();
    }

    private static void addArgumentValue(List<String> target, JsonElement value) throws IOException {
        if (value == null) throw new IOException("Minecraft launch metadata contains an empty argument.");
        if (value.isJsonArray()) {
            for (JsonElement entry : value.getAsJsonArray()) target.add(entry.getAsString());
        } else {
            target.add(value.getAsString());
        }
    }

    private static boolean rulesAllow(JsonObject item) {
        if (!item.has("rules")) return true;
        boolean allowed = false;
        for (JsonElement element : item.getAsJsonArray("rules")) {
            JsonObject rule = element.getAsJsonObject();
            if (!ruleMatches(rule)) continue;
            allowed = "allow".equals(rule.get("action").getAsString());
        }
        return allowed;
    }

    private static boolean ruleMatches(JsonObject rule) {
        if (rule.has("os")) {
            JsonObject os = rule.getAsJsonObject("os");
            if (os.has("name") && !"windows".equals(os.get("name").getAsString())) return false;
            if (os.has("arch")) {
                String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT).contains("64")
                        ? "x86_64" : "x86";
                if (!arch.matches(os.get("arch").getAsString())) return false;
            }
            if (os.has("version") && !System.getProperty("os.version")
                    .matches(os.get("version").getAsString())) return false;
        }
        if (rule.has("features")) {
            for (var entry : rule.getAsJsonObject("features").entrySet()) {
                boolean actual = FEATURES.getOrDefault(entry.getKey(), false);
                if (actual != entry.getValue().getAsBoolean()) return false;
            }
        }
        return true;
    }

    private static String substitute(String argument, Map<String, String> substitutions) throws IOException {
        String result = argument;
        for (var entry : substitutions.entrySet()) result = result.replace(entry.getKey(), entry.getValue());
        if (result.matches(".*\\$\\{[^}]+}.*")) {
            throw new IOException("Unsupported Minecraft launch variable in argument: " + result);
        }
        return result;
    }

    private static boolean hasClasspathOption(List<String> arguments) {
        return arguments.stream().anyMatch(argument -> "-cp".equals(argument)
                || "-classpath".equals(argument) || "--class-path".equals(argument));
    }

    private static String mavenPath(String coordinate) throws IOException {
        String[] parts = coordinate.split(":");
        if (parts.length < 3) throw new IOException("Invalid Minecraft library coordinate.");
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        String extension = "jar";
        int extensionSeparator = version.indexOf('@');
        if (extensionSeparator >= 0) {
            extension = version.substring(extensionSeparator + 1);
            version = version.substring(0, extensionSeparator);
        }
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version
                + classifier + "." + extension;
    }

    private static JsonObject readJson(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Minecraft version metadata is missing: " + path);
        return parseJson(Files.readString(path), "Minecraft version metadata");
    }

    private static JsonObject parseJson(String text, String description) throws IOException {
        try {
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Could not parse " + description + ".", exception);
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0;
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime.", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime.", exception);
        }
    }
}
