package dev.wvdoor.launcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ElyByAuthService {
    private static final URI AUTHENTICATE_URI = URI.create("https://authserver.ely.by/auth/authenticate");
    private final HttpClient http;
    private final URI authenticateUri;

    public ElyByAuthService() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), AUTHENTICATE_URI);
    }

    ElyByAuthService(HttpClient http, URI authenticateUri) {
        this.http = http;
        this.authenticateUri = authenticateUri;
    }

    public LoginResult authenticate(String username, char[] password, String twoFactorCode)
            throws IOException, InterruptedException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Enter your Ely.by username or email.");
        }
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Enter your Ely.by password.");
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("username", username.trim());
        String passwordValue = new String(password);
        if (twoFactorCode != null && !twoFactorCode.isBlank()) {
            passwordValue += ":" + twoFactorCode.trim();
        }
        requestBody.addProperty("password", passwordValue);
        requestBody.addProperty("clientToken", UUID.randomUUID().toString());
        requestBody.addProperty("requestUser", true);

        HttpRequest request = HttpRequest.newBuilder(authenticateUri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "DoorCLauncher")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject body = parseObject(response.body());
        if (response.statusCode() != 200) {
            String message = body != null && body.has("errorMessage")
                    ? body.get("errorMessage").getAsString()
                    : "Ely.by sign-in failed with HTTP " + response.statusCode() + ".";
            if (message.toLowerCase(java.util.Locale.ROOT).contains("two factor")
                    || message.toLowerCase(java.util.Locale.ROOT).contains("two-factor")) {
                throw new TwoFactorRequiredException();
            }
            throw new IOException(message);
        }
        if (body == null || !body.has("accessToken") || body.get("accessToken").isJsonNull()) {
            throw new IOException("Ely.by returned an incomplete authentication response.");
        }
        String accessToken = body.get("accessToken").getAsString();
        if (accessToken.isBlank()) throw new IOException("Ely.by returned an empty access token.");

        List<Profile> profiles = new ArrayList<>();
        if (body.has("availableProfiles") && body.get("availableProfiles").isJsonArray()) {
            JsonArray available = body.getAsJsonArray("availableProfiles");
            for (JsonElement element : available) {
                if (element.isJsonObject()) profiles.add(parseProfile(element.getAsJsonObject()));
            }
        }
        Profile selected = body.has("selectedProfile") && body.get("selectedProfile").isJsonObject()
                ? parseProfile(body.getAsJsonObject("selectedProfile")) : null;
        if (selected != null && profiles.stream().noneMatch(profile -> profile.id().equals(selected.id()))) {
            profiles.add(selected);
        }
        if (profiles.isEmpty()) {
            throw new IOException("Ely.by returned no Minecraft profiles for this account.");
        }
        return new LoginResult(accessToken, selected, List.copyOf(profiles));
    }

    private static Profile parseProfile(JsonObject json) throws IOException {
        try {
            return new Profile(json.get("name").getAsString(), json.get("id").getAsString());
        } catch (RuntimeException exception) {
            throw new IOException("Ely.by returned an invalid Minecraft profile.", exception);
        }
    }

    private static JsonObject parseObject(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public record Profile(String name, String id) {
        @Override
        public String toString() {
            return name;
        }
    }

    public record LoginResult(String accessToken, Profile selectedProfile, List<Profile> profiles) {
        public GameAccount account(Profile profile) {
            if (!profiles.contains(profile)) {
                throw new IllegalArgumentException("The selected Ely.by profile was not part of this login.");
            }
            return GameAccount.elyBy(profile.name(), profile.id(), accessToken);
        }

        @Override
        public String toString() {
            return "LoginResult[selectedProfile=" + selectedProfile + ", profiles=" + profiles + "]";
        }
    }

    public static final class TwoFactorRequiredException extends IOException {
        public TwoFactorRequiredException() {
            super("This Ely.by account requires a two-factor authentication code.");
        }
    }
}
