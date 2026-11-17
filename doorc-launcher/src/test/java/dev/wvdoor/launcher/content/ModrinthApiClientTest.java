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
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthApiClientTest {
    private HttpServer server;
    private String requestPath;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath = exchange.getRequestURI().toString();
            String body = exchange.getRequestURI().getPath().equals("/search")
                    ? """
                    {"hits":[{"project_id":"project-id","slug":"sample-mod","title":"Sample Mod",
                    "author":"Author","description":"Example","downloads":12}],"total_hits":1,
                    "offset":0,"limit":20}
                    """
                    : """
                    [{"id":"version-id","version_number":"1.0.0",
                    "files":[{"url":"https://cdn.modrinth.com/data/project-id/file.jar",
                    "filename":"sample.jar","primary":true,"size":4,
                    "hashes":{"sha512":"aabb"}}]}]
                    """;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void searchesCompatibleProjectsAndResolvesThePrimaryFile() throws Exception {
        ModrinthApiClient client = client();
        MinecraftProfile profile = new MinecraftProfile("Test", Path.of("game"),
                "1.21.11", "fabric", "0.19.5");
        ContentSearchPage page = client.search(new ContentSearchQuery(
                ContentType.MODS, "sample", profile, 0, 20));

        assertEquals(1, page.projects().size());
        assertEquals("Sample Mod", page.projects().get(0).title());
        assertTrue(requestPath.contains("project_type%3Amod"));
        assertTrue(requestPath.contains("versions%3A1.21.11"));
        assertTrue(requestPath.contains("categories%3Afabric"));

        ContentFile file = client.latestCompatibleFile(page.projects().get(0), profile);
        assertEquals("sample.jar", file.fileName());
        assertEquals("SHA-512", file.hashAlgorithm());
        assertEquals("https://cdn.modrinth.com/data/project-id/file.jar", file.downloadUri().toString());
    }

    @Test
    void rejectsUnsupportedMapSearchAndVanillaModSearch() {
        ModrinthApiClient client = client();
        MinecraftProfile vanilla = new MinecraftProfile("Vanilla",
                Path.of("game"), "1.21.11", "vanilla", "Vanilla");
        assertThrows(IOException.class, () -> client.search(new ContentSearchQuery(
                ContentType.MAPS, "", vanilla, 0, 20)));
        assertThrows(IOException.class, () -> client.search(new ContentSearchQuery(
                ContentType.MODS, "", vanilla, 0, 20)));
    }

    private ModrinthApiClient client() {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        return new ModrinthApiClient(HttpClient.newHttpClient(), endpoint);
    }
}
