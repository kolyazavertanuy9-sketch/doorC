package dev.wvdoor.launcher.auth;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElyByAuthServiceTest {
    private HttpServer server;
    private AtomicReference<String> requestBody;
    private String responseBody;
    private int responseCode;

    @BeforeEach
    void startServer() throws IOException {
        requestBody = new AtomicReference<>();
        responseCode = 200;
        responseBody = """
                {
                  "accessToken": "ely-token",
                  "selectedProfile": {"id": "ffc8fdc95824509e8a57c99b940fb996", "name": "Player"},
                  "availableProfiles": [
                    {"id": "ffc8fdc95824509e8a57c99b940fb996", "name": "Player"}
                  ]
                }
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/authenticate", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseCode, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void authenticatesAndCreatesAnElyByAccount() throws Exception {
        ElyByAuthService service = service();
        ElyByAuthService.LoginResult result =
                service.authenticate("player@example.com", "secret".toCharArray(), null);

        assertEquals("player@example.com", com.google.gson.JsonParser.parseString(requestBody.get())
                .getAsJsonObject().get("username").getAsString());
        assertEquals("secret", com.google.gson.JsonParser.parseString(requestBody.get())
                .getAsJsonObject().get("password").getAsString());
        assertEquals("Player", result.selectedProfile().name());
        GameAccount account = result.account(result.selectedProfile());
        assertEquals(GameAccount.Provider.ELY_BY, account.provider());
        assertEquals("ffc8fdc9-5824-509e-8a57-c99b940fb996", account.uuid());
        assertEquals("ely-token", account.accessToken());
        assertFalse(account.toString().contains("ely-token"));
        assertFalse(result.toString().contains("ely-token"));
    }

    @Test
    void reportsWhenTwoFactorCodeIsRequired() {
        responseCode = 401;
        responseBody = """
                {"error":"ForbiddenOperationException",
                 "errorMessage":"Account protected with two factor auth."}
                """;

        assertThrows(ElyByAuthService.TwoFactorRequiredException.class,
                () -> service().authenticate("player@example.com", "secret".toCharArray(), null));
    }

    @Test
    void sendsTwoFactorCodeAsElyByRequires() throws Exception {
        service().authenticate("player@example.com", "secret".toCharArray(), "123456");

        assertEquals("secret:123456", com.google.gson.JsonParser.parseString(requestBody.get())
                .getAsJsonObject().get("password").getAsString());
    }

    private ElyByAuthService service() {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                + "/auth/authenticate");
        return new ElyByAuthService(HttpClient.newHttpClient(), endpoint);
    }
}
