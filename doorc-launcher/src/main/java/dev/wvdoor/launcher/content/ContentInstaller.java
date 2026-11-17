package dev.wvdoor.launcher.content;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ContentInstaller {
    private static final long MAX_DOWNLOAD_BYTES = 1024L * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 100_000;
    private static final Pattern INVALID_FILENAME = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private final HttpClient http;

    public ContentInstaller() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    ContentInstaller(HttpClient http) {
        this.http = http;
    }

    public Path install(ContentProject project, ContentFile file, MinecraftProfile profile)
            throws IOException, InterruptedException {
        if (project.platform() != file.platform() || project.type() != file.type()
                || !project.id().equals(file.projectId())) {
            throw new IOException("The selected file does not belong to this project.");
        }
        String fileName = safeFileName(file.fileName());
        validateFileType(file.type(), fileName);
        Path staging = Files.createTempDirectory(profile.directory(), ".doorc-content-");
        try {
            Path downloaded = staging.resolve(fileName);
            download(file, downloaded);
            Path installed = switch (file.type()) {
                case MODS -> installFile(downloaded, profile.directory().resolve("mods"), fileName);
                case RESOURCE_PACKS -> installFile(downloaded,
                        profile.directory().resolve("resourcepacks"), fileName);
                case MAPS -> installWorld(downloaded, staging, profile.directory(), project.title());
            };
            deleteTree(staging);
            return installed;
        } catch (IOException | InterruptedException | RuntimeException exception) {
            try {
                deleteTree(staging);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    private void download(ContentFile file, Path destination) throws IOException, InterruptedException {
        requireTrustedDownload(file.platform(), file.downloadUri());
        if (file.size() > MAX_DOWNLOAD_BYTES) {
            throw new IOException("This content file exceeds the 1 GiB download limit.");
        }
        HttpRequest request = HttpRequest.newBuilder(file.downloadUri())
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "DoorCLauncher")
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            requireTrustedDownload(file.platform(), response.uri());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Content download failed with HTTP " + response.statusCode() + ".");
            }
            MessageDigest digest = digest(file.hashAlgorithm());
            AtomicLong received = new AtomicLong();
            try (OutputStream output = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    long total = received.addAndGet(count);
                    if (total > MAX_DOWNLOAD_BYTES || (file.size() >= 0 && total > file.size())) {
                        throw new IOException("Content download exceeded the expected file size.");
                    }
                    output.write(buffer, 0, count);
                    if (digest != null) digest.update(buffer, 0, count);
                }
            }
            if (file.size() >= 0 && received.get() != file.size()) {
                throw new IOException("Downloaded content size does not match provider metadata.");
            }
            if (digest != null && !HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(file.hash())) {
                throw new IOException("Downloaded content failed its provider hash check.");
            }
        }
    }

    private static Path installFile(Path source, Path destinationDirectory, String fileName) throws IOException {
        Files.createDirectories(destinationDirectory);
        Path destination = destinationDirectory.resolve(fileName).normalize();
        if (!destination.startsWith(destinationDirectory.toAbsolutePath().normalize())) {
            throw new IOException("The downloaded file name is not safe.");
        }
        try {
            return Files.move(source, destination);
        } catch (FileAlreadyExistsException exception) {
            throw new IOException("This file is already installed: " + fileName, exception);
        }
    }

    private static Path installWorld(Path archive, Path staging, Path instanceDirectory, String title)
            throws IOException {
        Path extracted = staging.resolve("world");
        Files.createDirectory(extracted);
        extractArchive(archive, extracted);
        Path levelData;
        try (Stream<Path> paths = Files.walk(extracted, 6)) {
            levelData = paths.filter(path -> !Files.isSymbolicLink(path)
                            && Files.isRegularFile(path)
                            && "level.dat".equalsIgnoreCase(path.getFileName().toString()))
                    .findFirst()
                    .orElseThrow(() -> new IOException("The downloaded map archive does not contain level.dat."));
        }
        Path worldDirectory = levelData.getParent();
        Path saves = instanceDirectory.resolve("saves");
        Files.createDirectories(saves);
        String directoryName = uniqueWorldName(saves, title);
        Path destination = saves.resolve(directoryName);
        try {
            return Files.move(worldDirectory, destination);
        } catch (FileAlreadyExistsException exception) {
            throw new IOException("A world with this name is already installed.", exception);
        }
    }

    static void extractArchive(Path archive, Path destination) throws IOException {
        long totalBytes = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ARCHIVE_ENTRIES) {
                    throw new IOException("The map archive contains too many files.");
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
                    throw new IOException("The map archive contains an absolute path.");
                }
                Path target = destination.resolve(name).normalize();
                if (!target.startsWith(destination)) {
                    throw new IOException("The map archive contains a path outside its destination.");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Path parent = target.getParent();
                    if (parent == null) throw new IOException("The map archive contains an invalid path.");
                    Files.createDirectories(parent);
                    try (OutputStream output = Files.newOutputStream(target)) {
                        byte[] buffer = new byte[32 * 1024];
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            totalBytes += count;
                            if (totalBytes > MAX_EXTRACTED_BYTES) {
                                throw new IOException("The extracted map exceeds the 2 GiB limit.");
                            }
                            output.write(buffer, 0, count);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static void requireTrustedDownload(ContentPlatform platform, URI uri) throws IOException {
        String host = uri.getHost();
        boolean trusted = switch (platform) {
            case MODRINTH -> "cdn.modrinth.com".equalsIgnoreCase(host);
            case CURSEFORGE -> host != null
                    && (host.equalsIgnoreCase("forgecdn.net")
                    || host.toLowerCase(Locale.ROOT).endsWith(".forgecdn.net"));
        };
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !trusted) {
            throw new IOException("The provider returned a download URL outside its trusted HTTPS CDN.");
        }
    }

    private static MessageDigest digest(String algorithm) throws IOException {
        if (algorithm == null || algorithm.isBlank()) return null;
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("Unsupported provider hash algorithm: " + algorithm, exception);
        }
    }

    private static void validateFileType(ContentType type, String fileName) throws IOException {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        boolean valid = switch (type) {
            case MODS -> lowerName.endsWith(".jar");
            case MAPS, RESOURCE_PACKS -> lowerName.endsWith(".zip");
        };
        if (!valid) throw new IOException("The provider file is not a supported " + type.displayName() + " archive.");
    }

    private static String safeFileName(String fileName) throws IOException {
        String safe = INVALID_FILENAME.matcher(fileName).replaceAll("_").strip();
        if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) {
            throw new IOException("The provider returned an invalid file name.");
        }
        return safe;
    }

    private static String uniqueWorldName(Path saves, String title) {
        String base = safeDirectoryName(title);
        String candidate = base;
        int suffix = 2;
        while (Files.exists(saves.resolve(candidate))) candidate = base + " (" + suffix++ + ")";
        return candidate;
    }

    private static String safeDirectoryName(String title) {
        String safe = INVALID_FILENAME.matcher(title == null ? "" : title).replaceAll("_").strip();
        if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) return "Downloaded World";
        return safe;
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
