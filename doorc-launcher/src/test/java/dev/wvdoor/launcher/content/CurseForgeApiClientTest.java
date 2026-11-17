package dev.wvdoor.launcher.content;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurseForgeApiClientTest {
    private HttpServer server;
    private String searchRequest;
    private String apiKeyHeader;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/categories")) {
                respond(exchange, """
                        {"data":[
                          {"id":6,"name":"Mods","isClass":true},
                          {"id":12,"name":"Texture Packs","isClass":true},
                          {"id":17,"name":"Worlds","isClass":true}
                        ]}
                        """);
            } else if (path.endsWith("/mods/search")) {
                searchRequest = exchange.getRequestURI().toString();
                apiKeyHeader = exchange.getRequestHeaders().getFirst("x-api-key");
                respond(exchange, """
                        {"data":[{"id":501,"name":"Sample Mod","slug":"sample-mod",
                          "summary":"Example project","downloadCount":123,
                          "allowModDistribution":true,"links":{"websiteUrl":"https://www.curseforge.com/sample"},
                          "authors":[{"name":"Author"}]}],
                         "pagination":{"index":0,"pageSize":20,"totalCount":1}}
                        """);
            } else {
                respond(exchange, """
                        {"data":[{"id":700,"fileName":"sample.jar","displayName":"Sample 1.0",
                          "isAvailable":true,"fileLength":4,"downloadUrl":"https://edge.forgecdn.net/files/sample.jar",
                          "gameVersions":["1.21.11"],"hashes":[{"algo":1,"value":"aabb"}]}]}
                        """);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void searchesAndSelectsTheCompatibleFileWithApiAuthentication() throws Exception {
        CurseForgeApiClient client = client("test-api-key");
        MinecraftProfile profile = new MinecraftProfile("Test", java.nio.file.Path.of("game"),
                "1.21.11", "fabric", "0.19.5");
        ContentSearchPage page = client.search(new ContentSearchQuery(
                ContentType.MODS, "sample mod", profile, 0, 20));

        assertEquals("test-api-key", apiKeyHeader);
        assertTrue(searchRequest.contains("classId=6"));
        assertTrue(searchRequest.contains("modLoaderType=4"));
        assertEquals("Sample Mod", page.projects().get(0).title());

        ContentFile file = client.latestCompatibleFile(page.projects().get(0), profile);
        assertEquals("sample.jar", file.fileName());
        assertEquals("SHA-1", file.hashAlgorithm());
        assertEquals("https://edge.forgecdn.net/files/sample.jar", file.downloadUri().toString());
    }

    @Test
    void refusesRequestsWithoutAnApiKey() {
        CurseForgeApiClient client = client("");
        MinecraftProfile profile = new MinecraftProfile("Test", java.nio.file.Path.of("game"),
                "1.21.11", "fabric", "0.19.5");
        assertThrows(IOException.class, () -> client.search(new ContentSearchQuery(
                ContentType.MODS, "", profile, 0, 20)));
    }

    private CurseForgeApiClient client(String apiKey) {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        return new CurseForgeApiClient(HttpClient.newHttpClient(), endpoint, apiKey);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
