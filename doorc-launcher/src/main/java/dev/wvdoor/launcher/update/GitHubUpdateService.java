package dev.wvdoor.launcher.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubUpdateService {
    private static final String DEFAULT_REPOSITORY = "kolyazavertanuy9-sketch/doorC";
    private static final long MAX_INSTALLER_SIZE = 256L * 1024 * 1024;
    private static final int MAX_CHECKSUM_SIZE = 16 * 1024;
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:\\+[0-9A-Za-z.-]+)?$");
    private static final Pattern SHA256_LINE_PATTERN =
            Pattern.compile("(?i)^([0-9a-f]{64})\\s+\\*?(.+)$");

    private final String repository;
    private final String currentVersion;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public GitHubUpdateService() {
        repository = System.getProperty("doorc.launcher.githubRepo", DEFAULT_REPOSITORY);
        currentVersion = System.getProperty("doorc.launcher.version", "0.0.0");
    }

    public String currentVersion() {
        return currentVersion;
    }

    public CheckResult checkForUpdate() throws IOException, InterruptedException {
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IOException("The configured GitHub release repository is invalid.");
        }
        URI endpoint = URI.create("https://api.github.com/repos/" + repository + "/releases/latest");
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "DoorC-Launcher/" + currentVersion)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 404) {
            verifyRepositoryIsPublic();
            return new CheckResult(null, Optional.empty());
        }
        if (response.statusCode() != 200) {
            throw new IOException("GitHub update check returned HTTP " + response.statusCode() + ".");
        }

        JsonObject release;
        try {
            release = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("GitHub returned an invalid release document.", exception);
        }
        String version = versionFromTag(requiredString(release, "tag_name"));
        if (!isNewerVersion(version, currentVersion)) {
            return new CheckResult(version, Optional.empty());
        }

        String installerName = "DoorCLauncher-" + version + ".exe";
        JsonArray assets = release.getAsJsonArray("assets");
        JsonObject installer = null;
        JsonObject checksum = null;
        if (assets != null) {
            for (JsonElement element : assets) {
                JsonObject asset = element.getAsJsonObject();
                String name = requiredString(asset, "name");
                if (installerName.equals(name)) installer = asset;
                if ((installerName + ".sha256").equals(name)) checksum = asset;
            }
        }
        if (installer == null || checksum == null) {
            throw new IOException("Release " + version
                    + " is missing its installer or SHA-256 checksum asset.");
        }

        URI installerUri = githubDownloadUri(requiredString(installer, "browser_download_url"));
        URI checksumUri = githubDownloadUri(requiredString(checksum, "browser_download_url"));
        String releaseUrl = requiredString(release, "html_url");
        if (installer.has("size") && installer.get("size").getAsLong() > MAX_INSTALLER_SIZE) {
            throw new IOException("The published launcher installer exceeds the permitted size.");
        }
        return new CheckResult(version, Optional.of(new Release(
                version,
                releaseUrl,
                release.has("body") && !release.get("body").isJsonNull()
                        ? release.get("body").getAsString() : "",
                installerName,
                installerUri,
                checksumUri)));
    }

    public Path downloadAndVerify(Release release) throws IOException, InterruptedException {
        String expectedHash = fetchChecksum(release.checksumUri(), release.installerName());
        Path updatesDirectory = localAppDataDirectory().resolve("DoorCLauncher").resolve("updates");
        Files.createDirectories(updatesDirectory);
        Path installer = updatesDirectory.resolve(release.installerName());
        if (Files.isRegularFile(installer) && expectedHash.equals(sha256(installer))) return installer;

        Path temporary = Files.createTempFile(updatesDirectory, "launcher-update-", ".part");
        try {
            HttpRequest request = HttpRequest.newBuilder(release.installerUri())
                    .timeout(Duration.ofMinutes(15))
                    .header("User-Agent", "DoorC-Launcher/" + currentVersion)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("Installer download returned HTTP " + response.statusCode() + ".");
                }
                long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (contentLength > MAX_INSTALLER_SIZE) {
                    throw new IOException("The downloaded installer exceeds the permitted size.");
                }
                copyLimited(input, temporary);
            }
            if (!expectedHash.equals(sha256(temporary))) {
                throw new IOException("The downloaded installer failed its SHA-256 integrity check.");
            }
            try {
                Files.move(temporary, installer, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, installer, StandardCopyOption.REPLACE_EXISTING);
            }
            return installer;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static boolean isNewerVersion(String candidate, String current) {
        Version candidateVersion = parseVersion(candidate);
        Version currentVersion = parseVersion(current);
        int comparison = candidateVersion.major().compareTo(currentVersion.major());
        if (comparison == 0) comparison = candidateVersion.minor().compareTo(currentVersion.minor());
        if (comparison == 0) comparison = candidateVersion.patch().compareTo(currentVersion.patch());
        return comparison > 0;
    }

    private String fetchChecksum(URI checksumUri, String installerName)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(checksumUri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "DoorC-Launcher/" + currentVersion)
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;
        try (InputStream input = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("SHA-256 asset download returned HTTP " + response.statusCode() + ".");
            }
            bytes = input.readNBytes(MAX_CHECKSUM_SIZE + 1);
        }
        if (bytes.length > MAX_CHECKSUM_SIZE) throw new IOException("The SHA-256 asset is too large.");
        String content = new String(bytes, StandardCharsets.UTF_8);
        return checksumFor(content, installerName);
    }

    private void verifyRepositoryIsPublic() throws IOException, InterruptedException {
        URI endpoint = URI.create("https://api.github.com/repos/" + repository);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "DoorC-Launcher/" + currentVersion)
                .GET()
                .build();
        HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub release repository is unavailable (HTTP "
                    + response.statusCode() + ").");
        }
    }

    static String checksumFor(String content, String installerName) throws IOException {
        for (String line : content.split("\\R")) {
            Matcher matcher = SHA256_LINE_PATTERN.matcher(line.trim());
            if (matcher.matches() && installerName.equals(matcher.group(2).trim())) {
                return matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            }
        }
        throw new IOException("The SHA-256 asset does not identify " + installerName + ".");
    }

    private static void copyLimited(InputStream input, Path destination) throws IOException {
        long copied = 0;
        byte[] buffer = new byte[32 * 1024];
        try (OutputStream output = Files.newOutputStream(destination)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                copied += count;
                if (copied > MAX_INSTALLER_SIZE) {
                    throw new IOException("The downloaded installer exceeds the permitted size.");
                }
                output.write(buffer, 0, count);
            }
        }
    }

    private static URI githubDownloadUri(String value) throws IOException {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IOException("GitHub returned an invalid release asset URL.", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"github.com".equalsIgnoreCase(uri.getHost())
                || uri.getPath() == null || !uri.getPath().contains("/releases/download/")) {
            throw new IOException("Release assets must be served from github.com over HTTPS.");
        }
        return uri;
    }

    static String versionFromTag(String tag) throws IOException {
        String version = tag.startsWith("launcher-v") ? tag.substring("launcher-v".length())
                : tag.startsWith("v") ? tag.substring(1) : tag;
        try {
            parseVersion(version);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Release tag must use launcher-vMAJOR.MINOR.PATCH format.", exception);
        }
        return version;
    }

    private static Version parseVersion(String value) {
        Matcher matcher = VERSION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid launcher version: " + value);
        }
        return new Version(new BigInteger(matcher.group(1)),
                new BigInteger(matcher.group(2)), new BigInteger(matcher.group(3)));
    }

    private static String requiredString(JsonObject object, String key) throws IOException {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new IOException("GitHub release is missing the " + key + " field.");
        }
        return object.get(key).getAsString();
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", exception);
        }
    }

    private static Path localAppDataDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        return localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home")) : Path.of(localAppData);
    }

    public record CheckResult(String latestVersion, Optional<Release> update) {
        public boolean hasPublishedRelease() {
            return latestVersion != null;
        }
    }

    public record Release(String version, String releaseUrl, String notes, String installerName,
                          URI installerUri, URI checksumUri) {}

    private record Version(BigInteger major, BigInteger minor, BigInteger patch) {}
}
