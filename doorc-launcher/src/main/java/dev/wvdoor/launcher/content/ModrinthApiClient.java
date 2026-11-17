package dev.wvdoor.launcher.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModrinthApiClient implements ContentProvider {
    private static final URI API = URI.create("https://api.modrinth.com/v2/");
    private final HttpClient http;
    private final URI apiBase;

    public ModrinthApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), API);
    }

    ModrinthApiClient(HttpClient http, URI apiBase) {
        this.http = http;
        this.apiBase = apiBase;
    }

    @Override
    public ContentPlatform platform() {
        return ContentPlatform.MODRINTH;
    }

    @Override
    public ContentSearchPage search(ContentSearchQuery query) throws IOException, InterruptedException {
        String projectType = query.type().modrinthProjectType();
        if (projectType == null) {
            throw new IOException("Modrinth does not have a maps/worlds project type. Use CurseForge for maps.");
        }
        if (query.type() == ContentType.MODS && "vanilla".equals(query.profile().loader())) {
            throw new IOException("Install a Fabric, Forge, NeoForge or Quilt profile before browsing mods.");
        }

        JsonArray facets = new JsonArray();
        addFacet(facets, "project_type:" + projectType);
        addFacet(facets, "versions:" + query.profile().gameVersion());
        if (query.type() == ContentType.MODS) {
            addFacet(facets, "categories:" + modrinthLoader(query.profile().loader()));
        }
        String url = "search?query=" + encode(query.query())
                + "&facets=" + encode(facets.toString())
                + "&offset=" + query.offset()
                + "&limit=" + query.limit()
                + "&index=" + encode("downloads");
        JsonObject response = getJson(url);
        JsonArray hits = response.getAsJsonArray("hits");
        List<ContentProject> projects = new ArrayList<>(hits.size());
        for (JsonElement element : hits) {
            JsonObject hit = element.getAsJsonObject();
            String id = string(hit, "project_id", "");
            String slug = string(hit, "slug", id);
            projects.add(new ContentProject(platform(), query.type(), id,
                    string(hit, "title", "Untitled project"),
                    string(hit, "author", "Unknown author"),
                    string(hit, "description", ""),
                    "https://modrinth.com/" + projectType + "/" + slug,
                    string(hit, "icon_url", ""),
                    number(hit, "downloads", 0), true));
        }
        return new ContentSearchPage(projects, number(response, "total_hits", projects.size()),
                Math.toIntExact(number(response, "offset", query.offset())),
                Math.toIntExact(number(response, "limit", query.limit())));
    }

    @Override
    public ContentFile latestCompatibleFile(ContentProject project, MinecraftProfile profile)
            throws IOException, InterruptedException {
        if (project.platform() != platform()) throw new IllegalArgumentException("Project belongs to another provider.");
        String url = "project/" + encodePath(project.id()) + "/version?game_versions="
                + encode("[\"" + escapeJson(profile.gameVersion()) + "\"]");
        if (project.type() == ContentType.MODS) {
            if ("vanilla".equals(profile.loader())) {
                throw new IOException("Mods require a profile with a mod loader.");
            }
            url += "&loaders=" + encode("[\"" + escapeJson(modrinthLoader(profile.loader())) + "\"]");
        }
        JsonArray versions = getJsonArray(url);
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            JsonArray files = version.getAsJsonArray("files");
            JsonObject selected = primaryFile(files);
            if (selected == null) continue;
            URI download = secureDownloadUri(string(selected, "url", ""));
            JsonObject hashes = selected.getAsJsonObject("hashes");
            String algorithm = hashes.has("sha512") ? "SHA-512" : hashes.has("sha1") ? "SHA-1" : "";
            String hash = algorithm.isEmpty() ? "" : string(hashes, algorithm.equals("SHA-512") ? "sha512" : "sha1", "");
            return new ContentFile(platform(), project.type(), project.id(),
                    string(version, "id", ""), string(version, "version_number", ""),
                    string(selected, "filename", ""), download,
                    number(selected, "size", -1), algorithm, hash);
        }
        throw new IOException("No compatible file was published for " + project.title() + ".");
    }

    private JsonObject getJson(String path) throws IOException, InterruptedException {
        return JsonParser.parseString(getText(path)).getAsJsonObject();
    }

    private JsonArray getJsonArray(String path) throws IOException, InterruptedException {
        return JsonParser.parseString(getText(path)).getAsJsonArray();
    }

    private String getText(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBase.resolve(path))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "kolyazavertanuy9-sketch/DoorCLauncher/"
                        + System.getProperty("doorc.launcher.version", "development")
                        + " (https://github.com/kolyazavertanuy9-sketch/doorC)")
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Modrinth request failed with HTTP " + response.statusCode() + ".");
        }
        return response.body();
    }

    private URI secureDownloadUri(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Modrinth returned an invalid download URL.", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"cdn.modrinth.com".equalsIgnoreCase(uri.getHost())) {
            throw new IOException("Modrinth returned a download URL outside its trusted CDN.");
        }
        return uri;
    }

    private static JsonObject primaryFile(JsonArray files) {
        JsonObject first = null;
        for (JsonElement element : files) {
            JsonObject file = element.getAsJsonObject();
            if (first == null) first = file;
            if (file.has("primary") && file.get("primary").getAsBoolean()) return file;
        }
        return first;
    }

    private static String modrinthLoader(String loader) throws IOException {
        String normalized = loader.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "fabric", "forge", "neoforge", "quilt" -> normalized;
            default -> throw new IOException("Unsupported Minecraft mod loader: " + loader);
        };
    }

    private static void addFacet(JsonArray facets, String facet) {
        JsonArray group = new JsonArray();
        group.add(facet);
        facets.add(group);
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : fallback;
    }

    private static long number(JsonObject object, String key, long fallback) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsLong() : fallback;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }
}
