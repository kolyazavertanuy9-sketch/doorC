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
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CurseForgeApiClient implements ContentProvider {
    private static final URI API = URI.create("https://api.curseforge.com/");
    private static final int MINECRAFT_GAME_ID = 432;
    private final HttpClient http;
    private final URI apiBase;
    private final String apiKey;
    private volatile Map<ContentType, Integer> classIds;

    public CurseForgeApiClient() {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(20))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                API, System.getenv("CURSEFORGE_API_KEY"));
    }

    CurseForgeApiClient(HttpClient http, URI apiBase, String apiKey) {
        this.http = http;
        this.apiBase = apiBase;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    @Override
    public ContentPlatform platform() {
        return ContentPlatform.CURSEFORGE;
    }

    @Override
    public ContentSearchPage search(ContentSearchQuery query) throws IOException, InterruptedException {
        requireApiKey();
        if (query.type() == ContentType.MODS && "vanilla".equals(query.profile().loader())) {
            throw new IOException("Install a Fabric, Forge, NeoForge or Quilt profile before browsing mods.");
        }
        int classId = classIds().get(query.type());
        Map<String, String> parameters = new java.util.LinkedHashMap<>();
        parameters.put("gameId", Integer.toString(MINECRAFT_GAME_ID));
        parameters.put("classId", Integer.toString(classId));
        parameters.put("gameVersion", query.profile().gameVersion());
        if (!query.query().isBlank()) parameters.put("searchFilter", query.query());
        Integer loaderType = curseForgeLoaderType(query.profile().loader());
        if (query.type() == ContentType.MODS && loaderType != null) {
            parameters.put("modLoaderType", Integer.toString(loaderType));
        }
        parameters.put("index", Integer.toString(query.offset()));
        parameters.put("pageSize", Integer.toString(query.limit()));

        JsonObject response = getJson("v1/mods/search?" + queryString(parameters));
        JsonArray data = response.getAsJsonArray("data");
        List<ContentProject> projects = new ArrayList<>(data.size());
        for (JsonElement element : data) {
            JsonObject mod = element.getAsJsonObject();
            String id = mod.get("id").getAsString();
            JsonObject links = object(mod, "links");
            JsonObject logo = object(mod, "logo");
            JsonArray authors = array(mod, "authors");
            String author = authors.isEmpty() ? "Unknown author"
                    : string(authors.get(0).getAsJsonObject(), "name", "Unknown author");
            String slug = string(mod, "slug", id);
            String categoryPath = switch (query.type()) {
                case MODS -> "mc-mods";
                case MAPS -> "worlds";
                case RESOURCE_PACKS -> "texture-packs";
            };
            projects.add(new ContentProject(platform(), query.type(), id,
                    string(mod, "name", "Untitled project"), author,
                    string(mod, "summary", ""),
                    string(links, "websiteUrl", "https://www.curseforge.com/minecraft/"
                            + categoryPath + "/" + slug),
                    string(logo, "url", ""), number(mod, "downloadCount", 0),
                    mod.has("allowModDistribution") && mod.get("allowModDistribution").getAsBoolean()));
        }
        JsonObject pagination = object(response, "pagination");
        int total = (int) number(pagination, "totalCount", query.offset() + projects.size());
        return new ContentSearchPage(projects, total, query.offset(), query.limit());
    }

    @Override
    public ContentFile latestCompatibleFile(ContentProject project, MinecraftProfile profile)
            throws IOException, InterruptedException {
        requireApiKey();
        if (project.platform() != platform()) throw new IllegalArgumentException("Project belongs to another provider.");
        if (project.type() == ContentType.MODS && "vanilla".equals(profile.loader())) {
            throw new IOException("Mods require a profile with a mod loader.");
        }
        if (!project.downloadAllowed()) {
            throw new IOException("CurseForge does not allow third-party downloads for " + project.title() + ".");
        }
        Map<String, String> parameters = new java.util.LinkedHashMap<>();
        parameters.put("gameVersion", profile.gameVersion());
        parameters.put("pageSize", "50");
        Integer loaderType = curseForgeLoaderType(profile.loader());
        if (project.type() == ContentType.MODS && loaderType != null) {
            parameters.put("modLoaderType", Integer.toString(loaderType));
        }
        JsonObject response = getJson("v1/mods/" + encodePath(project.id()) + "/files?"
                + queryString(parameters));
        JsonArray files = response.getAsJsonArray("data");
        for (JsonElement element : files) {
            JsonObject file = element.getAsJsonObject();
            if (file.has("isAvailable") && !file.get("isAvailable").getAsBoolean()) continue;
            if (!supportsGameVersion(file, profile.gameVersion())) continue;
            String fileName = string(file, "fileName", "");
            if (fileName.isBlank()) continue;
            String downloadUrl = string(file, "downloadUrl", "");
            if (downloadUrl.isBlank()) {
                downloadUrl = getDownloadUrl(project.id(), file.get("id").getAsLong());
            }
            URI uri = secureDownloadUri(downloadUrl);
            HashValue hash = hash(file);
            return new ContentFile(platform(), project.type(), project.id(),
                    Long.toString(number(file, "id", 0)),
                    string(file, "displayName", string(file, "fileName", "")),
                    fileName, uri, number(file, "fileLength", -1),
                    hash.algorithm(), hash.value());
        }
        throw new IOException("CurseForge has no compatible downloadable file for " + project.title() + ".");
    }

    private Map<ContentType, Integer> classIds() throws IOException, InterruptedException {
        Map<ContentType, Integer> result = classIds;
        if (result != null) return result;
        synchronized (this) {
            if (classIds != null) return classIds;
            requireApiKey();
            JsonArray categories = getJson("v1/categories?gameId=" + MINECRAFT_GAME_ID)
                    .getAsJsonArray("data");
            EnumMap<ContentType, Integer> discovered = new EnumMap<>(ContentType.class);
            for (JsonElement element : categories) {
                JsonObject category = element.getAsJsonObject();
                if (!category.has("isClass") || !category.get("isClass").getAsBoolean()) continue;
                String name = string(category, "name", "").toLowerCase(Locale.ROOT);
                for (ContentType type : ContentType.values()) {
                    if (matchesCategory(type, name)) {
                        discovered.put(type, category.get("id").getAsInt());
                    }
                }
            }
            for (ContentType type : ContentType.values()) {
                if (!discovered.containsKey(type)) {
                    throw new IOException("CurseForge did not return a " + type.curseForgeClassName()
                            + " category for Minecraft.");
                }
            }
            classIds = Map.copyOf(discovered);
            return classIds;
        }
    }

    private String getDownloadUrl(String modId, long fileId) throws IOException, InterruptedException {
        JsonObject response = getJson("v1/mods/" + encodePath(modId) + "/files/" + fileId + "/download-url");
        String url = response.has("data") && !response.get("data").isJsonNull()
                ? response.get("data").getAsString() : "";
        if (url.isBlank()) {
            throw new IOException("CurseForge did not provide a downloadable URL for this file.");
        }
        return url;
    }

    private JsonObject getJson(String path) throws IOException, InterruptedException {
        requireApiKey();
        HttpRequest request = HttpRequest.newBuilder(apiBase.resolve(path))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "DoorCLauncher")
                .header("Accept", "application/json")
                .header("x-api-key", apiKey)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = response.statusCode() == 401 || response.statusCode() == 403
                    ? " Check that CURSEFORGE_API_KEY is valid and enabled."
                    : "";
            throw new IOException("CurseForge request failed with HTTP " + response.statusCode() + "." + detail);
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private URI secureDownloadUri(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new IOException("CurseForge returned an invalid download URL.", exception);
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || !(host.equalsIgnoreCase("forgecdn.net") || host.toLowerCase(Locale.ROOT).endsWith(".forgecdn.net"))) {
            throw new IOException("CurseForge returned a download URL outside its trusted CDN.");
        }
        return uri;
    }

    private static boolean supportsGameVersion(JsonObject file, String gameVersion) {
        if (!file.has("gameVersions") || !file.get("gameVersions").isJsonArray()) return false;
        for (JsonElement version : file.getAsJsonArray("gameVersions")) {
            if (gameVersion.equals(version.getAsString())) return true;
        }
        return false;
    }

    private static HashValue hash(JsonObject file) {
        JsonArray hashes = array(file, "hashes");
        for (JsonElement element : hashes) {
            JsonObject value = element.getAsJsonObject();
            int algorithm = (int) number(value, "algo", -1);
            String hash = string(value, "value", "");
            if (algorithm == 1) return new HashValue("SHA-1", hash);
            if (algorithm == 2) return new HashValue("MD5", hash);
        }
        return new HashValue("", "");
    }

    private static boolean matchesCategory(ContentType type, String name) {
        return switch (type) {
            case MODS -> name.equals("mods");
            case MAPS -> name.equals("worlds") || name.equals("world");
            case RESOURCE_PACKS -> name.equals("resource packs") || name.equals("texture packs");
        };
    }

    private static Integer curseForgeLoaderType(String loader) {
        return switch (loader.toLowerCase(Locale.ROOT)) {
            case "forge" -> 1;
            case "fabric" -> 4;
            case "quilt" -> 5;
            case "neoforge" -> 6;
            default -> null;
        };
    }

    private void requireApiKey() throws IOException {
        if (apiKey.isBlank()) {
            throw new IOException("CurseForge requires an API key. Set the CURSEFORGE_API_KEY environment variable.");
        }
    }

    private static JsonObject object(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject()
                ? object.getAsJsonObject(key) : new JsonObject();
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray()
                ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : fallback;
    }

    private static long number(JsonObject object, String key, long fallback) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsLong() : fallback;
    }

    private static String queryString(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    private record HashValue(String algorithm, String value) {}
}
