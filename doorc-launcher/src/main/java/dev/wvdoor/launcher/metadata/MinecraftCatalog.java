package dev.wvdoor.launcher.metadata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class MinecraftCatalog {
    private static final String MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String FORGE_PROMOTIONS_URL =
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String FORGE_MAVEN_METADATA =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    private static final String NEOFORGE_VERSIONS_URL =
            "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge";
    private static final String FABRIC_META = "https://meta.fabricmc.net";
    private static final String QUILT_META = "https://meta.quiltmc.org";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<GameVersion> getGameVersions() throws IOException, InterruptedException {
        JsonObject manifest = getJson(MANIFEST_URL);
        JsonArray versions = manifest.getAsJsonArray("versions");
        List<GameVersion> result = new ArrayList<>(versions.size());
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            String type = version.get("type").getAsString();
            result.add(new GameVersion(
                    version.get("id").getAsString(),
                    type,
                    version.get("url").getAsString(),
                    !"release".equals(type)));
        }
        return List.copyOf(result);
    }

    public JsonObject getVersionDetails(GameVersion version) throws IOException, InterruptedException {
        return getJson(version.url());
    }

    public List<String> getLoaderVersions(String gameVersion, Loader loader)
            throws IOException, InterruptedException {
        return switch (loader) {
            case VANILLA -> List.of("Vanilla");
            case FABRIC -> fabricVersions(gameVersion);
            case FORGE -> forgeVersions(gameVersion);
            case NEOFORGE -> neoForgeVersions(gameVersion);
            case QUILT -> quiltVersions(gameVersion);
        };
    }

    public String getFabricInstallerUrl() throws IOException, InterruptedException {
        JsonArray installers = getJsonArray(FABRIC_META + "/v2/versions/installer");
        for (JsonElement entry : installers) {
            JsonObject installer = entry.getAsJsonObject();
            if (installer.get("stable").getAsBoolean()) {
                return installer.get("url").getAsString();
            }
        }
        throw new IOException("Fabric did not publish a stable installer.");
    }

    public String getQuiltInstallerUrl() throws IOException, InterruptedException {
        JsonArray installers = getJsonArray(QUILT_META + "/v3/versions/installer");
        if (installers.isEmpty()) throw new IOException("Quilt did not publish an installer.");
        return installers.get(0).getAsJsonObject().get("url").getAsString();
    }

    public String getForgeInstallerUrl(String fullLoaderVersion) {
        return "https://maven.minecraftforge.net/net/minecraftforge/forge/"
                + fullLoaderVersion + "/forge-" + fullLoaderVersion + "-installer.jar";
    }

    public String getNeoForgeInstallerUrl(String loaderVersion) {
        return "https://maven.neoforged.net/releases/net/neoforged/neoforge/"
                + loaderVersion + "/neoforge-" + loaderVersion + "-installer.jar";
    }

    private List<String> fabricVersions(String gameVersion) throws IOException, InterruptedException {
        JsonArray versions = getJsonArray(FABRIC_META + "/v2/versions/loader/" + pathSegment(gameVersion));
        List<String> result = new ArrayList<>();
        for (JsonElement element : versions) {
            JsonObject loader = element.getAsJsonObject().getAsJsonObject("loader");
            if (loader.get("stable").getAsBoolean()) result.add(loader.get("version").getAsString());
        }
        return List.copyOf(result);
    }

    private List<String> quiltVersions(String gameVersion) throws IOException, InterruptedException {
        JsonArray versions = getJsonArray(QUILT_META + "/v3/versions/loader/" + pathSegment(gameVersion));
        List<String> result = new ArrayList<>();
        for (JsonElement element : versions) {
            JsonObject loader = element.getAsJsonObject().getAsJsonObject("loader");
            result.add(loader.get("version").getAsString());
        }
        return List.copyOf(result);
    }

    private List<String> forgeVersions(String gameVersion) throws IOException, InterruptedException {
        JsonObject promotions = getJson(FORGE_PROMOTIONS_URL).getAsJsonObject("promos");
        List<String> result = new ArrayList<>();
        for (String channel : List.of("recommended", "latest")) {
            String value = gameVersion + "-" + channel;
            if (promotions.has(value)) {
                String version = promotions.get(value).getAsString();
                String fullVersion = gameVersion + "-" + version;
                if (!result.contains(fullVersion)) result.add(fullVersion);
            }
        }
        if (result.isEmpty()) {
            result.addAll(parseForgeMavenVersions(getBytes(FORGE_MAVEN_METADATA), gameVersion));
        }
        return List.copyOf(result);
    }

    private List<String> neoForgeVersions(String gameVersion) throws IOException, InterruptedException {
        JsonObject metadata = getJson(NEOFORGE_VERSIONS_URL);
        JsonArray versions = metadata.getAsJsonArray("versions");
        String prefix = neoForgeGamePrefix(gameVersion) + ".";
        List<String> result = new ArrayList<>();
        for (JsonElement element : versions) {
            String version = element.getAsString();
            if (version.startsWith(prefix)) result.add(version);
        }
        result.sort(Comparator.reverseOrder());
        return List.copyOf(result);
    }

    private JsonObject getJson(String url) throws IOException, InterruptedException {
        return JsonParser.parseString(getText(url)).getAsJsonObject();
    }

    private JsonArray getJsonArray(String url) throws IOException, InterruptedException {
        return JsonParser.parseString(getText(url)).getAsJsonArray();
    }

    private String getText(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "DoorCLauncher/1.0")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Catalog request failed with HTTP " + response.statusCode()
                    + " from " + URI.create(url).getHost());
        }
        return response.body();
    }

    private byte[] getBytes(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "DoorCLauncher/1.0")
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Catalog request failed with HTTP " + response.statusCode()
                    + " from " + URI.create(url).getHost());
        }
        return response.body();
    }

    private static List<String> parseForgeMavenVersions(byte[] metadata, String gameVersion)
            throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Element root = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(metadata)).getDocumentElement();
            NodeList versions = root.getElementsByTagName("version");
            List<String> result = new ArrayList<>();
            String prefix = gameVersion + "-";
            for (int i = 0; i < versions.getLength(); i++) {
                String version = versions.item(i).getTextContent().trim();
                if (version.startsWith(prefix)) result.add(version);
            }
            result.sort(Comparator.reverseOrder());
            return result;
        } catch (Exception exception) {
            throw new IOException("Could not parse Forge version metadata.", exception);
        }
    }

    private static String neoForgeGamePrefix(String gameVersion) {
        String[] parts = gameVersion.split("\\.");
        if (parts.length < 3 || !parts[0].equals("1")) {
            throw new IllegalArgumentException("Unsupported NeoForge game version: " + gameVersion);
        }
        return parts[1] + "." + parts[2];
    }

    private static String pathSegment(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record GameVersion(String id, String type, String url, boolean isSnapshot) {
        @Override
        public String toString() {
            return id;
        }
    }

    public enum Loader {
        VANILLA("Vanilla"),
        FABRIC("Fabric"),
        FORGE("Forge"),
        NEOFORGE("NeoForge"),
        QUILT("Quilt");

        private final String displayName;

        Loader(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
