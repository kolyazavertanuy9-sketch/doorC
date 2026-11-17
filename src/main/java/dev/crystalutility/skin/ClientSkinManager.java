package dev.crystalutility.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.authlib.GameProfile;
import dev.crystalutility.CrystalUtilityClient;
import dev.crystalutility.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import dev.crystalutility.text.UiText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ClientSkinManager {
    private static final Path SKIN_DIRECTORY = FabricLoader.getInstance().getConfigDir()
            .resolve("crystalutility").resolve("skins");
    private static final Path LEGACY_SKIN_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("crystalutility").resolve("custom-skin.png");
    private static final Identifier SKIN_TEXTURE = Identifier.fromNamespaceAndPath(
            CrystalUtilityClient.MOD_ID, "custom_skin/local");
    private static final long UPLOAD_DELAY_NANOS = TimeUnit.MINUTES.toNanos(10);
    private static final String ENTITLEMENTS_URL = "https://api.minecraftservices.com/entitlements/mcstore";
    private static final String UPLOAD_URL = "https://api.minecraftservices.com/minecraft/profile/skins";
    private static final String USERNAME_LOOKUP_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String SKIN_TEXTURE_HOST = "textures.minecraft.net";
    private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
            .build();
    private static final ExecutorService UPLOAD_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "wvDoorClient-SkinUpload");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService SKIN_FETCH_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "wvDoorClient-SkinFetch");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile PlayerSkin localSkin;
    private static volatile PlayerSkin accountSkin;
    private static volatile java.util.UUID accountSkinProfileId;
    private static volatile boolean accountSkinRequestInProgress;
    private static volatile boolean accountSkinFallbackAttempted;
    private static Identifier accountSkinTexture;
    private static volatile String status = "Choose a 64x64 PNG skin.";
    private static volatile boolean previewSelected;
    private static final Map<String, Identifier> THUMBNAILS = new HashMap<>();
    private static long appliedAtNanos;
    private static boolean uploadAttempted;

    private ClientSkinManager() {}

    public static void loadSavedSkin(Minecraft client) {
        ModConfig config = ModConfig.get();
        try {
            Files.createDirectories(SKIN_DIRECTORY);
            migrateLegacySkin(config);
            Path selectedSkin = selectedSkinFile(config);
            if (selectedSkin == null) {
                config.customSkinEnabled = false;
                ModConfig.save();
                return;
            }
            registerSkin(client, selectedSkin, modelType(config));
            if (config.customSkinEnabled) {
                appliedAtNanos = 0;
                uploadAttempted = config.customSkinUploadCompleted || !config.customSkinUploadAllowed;
                status = config.customSkinUploadCompleted
                        ? "Skin is active locally and already uploaded to your profile."
                        : config.customSkinUploadAllowed
                                ? "Skin is active locally. The 10-minute timer starts in a world."
                                : "Downloaded profile skin is active locally; official upload is disabled.";
            } else {
                status = "Saved skin ready. Apply it from Skin Studio to start the timer.";
            }
        } catch (IOException exception) {
            config.customSkinEnabled = false;
            ModConfig.save();
            status = "Could not load the saved skin PNG.";
        }
    }

    public static void chooseSkin(Minecraft client, Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("Choose an existing PNG file.");
        }
        if (Files.size(source) > 1_048_576) {
            throw new IOException("Skin PNG must be 1 MiB or smaller.");
        }
        validateSkin(source);
        Files.createDirectories(SKIN_DIRECTORY);
        String fileName = uniqueSkinName(source.getFileName().toString());
        Path destination = SKIN_DIRECTORY.resolve(fileName);
        Files.copy(source, destination);
        ModConfig config = ModConfig.get();
        config.customSkinFile = fileName;
        config.customSkinEnabled = false;
        config.customSkinUploadCompleted = false;
        ModConfig.save();
        registerSkin(client, destination, modelType(config));
        appliedAtNanos = 0;
        uploadAttempted = false;
        status = "Added to your wardrobe. Apply it to start the timer.";
    }

    public static CompletableFuture<String> fetchAndWearSkin(Minecraft client, String username) {
        String cleanName = username == null ? "" : username.trim();
        if (!cleanName.matches("[A-Za-z0-9_]{1,16}")) {
            return CompletableFuture.failedFuture(
                    new IOException("Enter a valid Minecraft username (1-16 letters, numbers, or underscores)."));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fetchProfileSkin(cleanName);
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new CompletionException(exception);
            }
        }, SKIN_FETCH_EXECUTOR).thenCompose(fetched -> {
            CompletableFuture<String> result = new CompletableFuture<>();
            client.execute(() -> {
                try {
                    validateSkin(fetched.png());
                    Files.createDirectories(SKIN_DIRECTORY);
                    String fileName = uniqueSkinName(fetched.username() + ".png");
                    Path destination = SKIN_DIRECTORY.resolve(fileName);
                    Files.write(destination, fetched.png(), StandardOpenOption.CREATE_NEW);

                    ModConfig config = ModConfig.get();
                    config.customSkinFile = fileName;
                    config.customSkinSlimModel = fetched.slim();
                    config.customSkinEnabled = true;
                    config.customSkinUploadCompleted = false;
                    config.customSkinUploadAllowed = false;
                    ModConfig.save();
                    registerSkin(client, destination, modelType(config));
                    appliedAtNanos = 0;
                    uploadAttempted = true;
                    status = "Downloaded " + fetched.username() + "'s skin; wearing it locally.";
                    result.complete(status);
                } catch (IOException exception) {
                    result.completeExceptionally(exception);
                }
            });
            return result;
        });
    }

    public static java.util.List<String> skinLibrary() {
        try {
            Files.createDirectories(SKIN_DIRECTORY);
            try (var skins = Files.list(SKIN_DIRECTORY)) {
                return skins.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                                .endsWith(".png"))
                        .map(path -> path.getFileName().toString())
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
            }
        } catch (IOException exception) {
            System.err.println("[wvDoorClient] Could not list skin library: " + exception.getMessage());
            return java.util.List.of();
        }
    }

    public static String selectedSkinName() {
        return ModConfig.get().customSkinFile;
    }

    public static void drawSkinThumbnail(Minecraft client, net.minecraft.client.gui.GuiGraphics graphics,
                                         String fileName, int x, int y, int size) {
        Identifier texture = THUMBNAILS.get(fileName);
        if (texture == null) {
            Path path = SKIN_DIRECTORY.resolve(fileName).normalize();
            if (!isSafeSkinName(fileName) || !path.getParent().equals(SKIN_DIRECTORY.normalize())) return;
            try {
                if (!Files.isRegularFile(path) || Files.size(path) > 1_048_576) return;
                try (InputStream input = Files.newInputStream(path)) {
                    NativeImage image = NativeImage.read(input);
                    if (image.getWidth() != 64 || image.getHeight() != 64) {
                        image.close();
                        return;
                    }
                    texture = Identifier.fromNamespaceAndPath(CrystalUtilityClient.MOD_ID,
                            "skin_thumbnail/" + UUID.nameUUIDFromBytes(
                                    fileName.getBytes(StandardCharsets.UTF_8)).toString().replace("-", ""));
                    client.getTextureManager().register(texture,
                            new DynamicTexture(() -> "wvDoorClient skin thumbnail", image));
                    THUMBNAILS.put(fileName, texture);
                }
            } catch (IOException exception) {
                System.err.println("[wvDoorClient] Could not load skin thumbnail: " + exception.getMessage());
                return;
            }
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 8, 8,
                size, size, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 40, 8,
                size, size, 8, 8, 64, 64);
    }

    public static void releaseSkinThumbnails(Minecraft client) {
        for (Identifier texture : THUMBNAILS.values()) {
            client.getTextureManager().release(texture);
        }
        THUMBNAILS.clear();
    }

    public static Path choosePngFile() throws IOException {
        if (GraphicsEnvironment.isHeadless()) {
            if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
                return chooseWindowsPngFile();
            }
            throw new IOException("The operating system file dialog is unavailable in this environment.");
        }
        java.util.concurrent.atomic.AtomicReference<Path> selected = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<RuntimeException> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Runnable showDialog = () -> {
            try {
                FileDialog dialog = new FileDialog((Frame) null,
                        "Choose a Minecraft skin PNG (64x64)", FileDialog.LOAD);
                dialog.setFilenameFilter((directory, name) ->
                        name.toLowerCase(java.util.Locale.ROOT).endsWith(".png"));
                dialog.setFile("*.png");
                dialog.setVisible(true);
                String file = dialog.getFile();
                String directory = dialog.getDirectory();
                if (file != null && directory != null) {
                    selected.set(Path.of(directory, file));
                }
                dialog.dispose();
            } catch (RuntimeException exception) {
                failure.set(exception);
            }
        };
        try {
            if (java.awt.EventQueue.isDispatchThread()) {
                showDialog.run();
            } else {
                java.awt.EventQueue.invokeAndWait(showDialog);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Opening the system file dialog was interrupted.", exception);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            throw new IOException("Could not open the operating system file dialog.", exception.getCause());
        }
        if (failure.get() != null) {
            throw new IOException("Could not open the operating system file dialog.", failure.get());
        }
        return selected.get();
    }

    private static Path chooseWindowsPngFile() throws IOException {
        Path resultFile = Files.createTempFile("wvdoor-skin-picker-", ".path");
        String quotedResultPath = resultFile.toAbsolutePath().toString().replace("'", "''");
        String script = """
                $ErrorActionPreference = 'Stop'
                Add-Type -AssemblyName System.Windows.Forms
                $dialog = New-Object System.Windows.Forms.OpenFileDialog
                $dialog.Title = 'Choose a Minecraft skin PNG (64x64)'
                $dialog.Filter = 'Minecraft skins (*.png)|*.png'
                $dialog.Multiselect = $false
                $dialog.CheckFileExists = $true
                try {
                    if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
                        [System.IO.File]::WriteAllText('%s', $dialog.FileName,
                            [System.Text.UTF8Encoding]::new($false))
                    }
                } finally {
                    $dialog.Dispose()
                }
                """.formatted(quotedResultPath);
        String encodedScript = java.util.Base64.getEncoder().encodeToString(
                script.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
        Process process = null;
        try {
            process = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA",
                    "-WindowStyle", "Hidden", "-EncodedCommand", encodedScript)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(5, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("The Windows file dialog did not close in time.");
            }
            if (process.exitValue() != 0) {
                throw new IOException("Could not open the Windows file dialog (PowerShell exit code "
                        + process.exitValue() + ").");
            }
            String selectedPath = Files.readString(resultFile, StandardCharsets.UTF_8);
            return selectedPath.isBlank() ? null : Path.of(selectedPath);
        } catch (InterruptedException exception) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Opening the Windows file dialog was interrupted.", exception);
        } finally {
            Files.deleteIfExists(resultFile);
        }
    }

    public static void selectSkin(Minecraft client, String fileName) {
        if (fileName == null || fileName.isBlank() || !isSafeSkinName(fileName)) return;
        Path path = SKIN_DIRECTORY.resolve(fileName).normalize();
        if (!path.getParent().equals(SKIN_DIRECTORY.normalize()) || !Files.isRegularFile(path)) return;
        try {
            registerSkin(client, path, modelType(ModConfig.get()));
            ModConfig config = ModConfig.get();
            config.customSkinFile = fileName;
            config.customSkinEnabled = false;
            config.customSkinUploadCompleted = false;
            ModConfig.save();
            appliedAtNanos = 0;
            uploadAttempted = false;
            status = "Skin selected. Apply it to wear.";
        } catch (IOException exception) {
            status = "Could not load the selected skin PNG.";
        }
    }

    public static void beginPreview() {
        previewSelected = true;
    }

    public static void endPreview() {
        previewSelected = false;
    }

    public static void drawAccountAvatar(Minecraft client, GuiGraphics graphics,
                                         int left, int top, int right, int bottom,
                                         int scale, int mouseX, int mouseY, float partialTick) {
        PlayerSkin skin = avatarSkin(client);
        if (skin == null) {
            graphics.drawCenteredString(client.font, UiText.tr("Loading skin..."),
                    (left + right) / 2, (top + bottom) / 2, 0xFFBBC5D2);
            return;
        }

        AvatarRenderState state = new AvatarRenderState();
        state.entityType = EntityType.PLAYER;
        state.skin = skin;
        state.boundingBoxWidth = 0.6F;
        state.boundingBoxHeight = 1.8F;
        state.eyeHeight = 1.62F;
        state.lightCoords = 15728880;
        state.pose = Pose.STANDING;
        state.scale = 1.0F;
        state.ageScale = 1.0F;
        state.mainArm = HumanoidArm.RIGHT;
        state.attackArm = HumanoidArm.RIGHT;
        state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        state.showHat = true;
        state.showJacket = true;
        state.showLeftPants = true;
        state.showRightPants = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;

        float centerX = (left + right) / 2.0F;
        float centerY = (top + bottom) / 2.0F;
        float horizontalAngle = (float) Math.atan((centerX - mouseX) / 40.0F);
        float verticalAngle = (float) Math.atan((centerY - mouseY) / 40.0F);
        state.bodyRot = 180.0F + horizontalAngle * 20.0F;
        state.yRot = horizontalAngle * 20.0F;
        state.xRot = -verticalAngle * 20.0F;

        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(
                verticalAngle * 20.0F * ((float) Math.PI / 180.0F));
        pose.mul(camera);
        Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + scale, 0.0F);
        graphics.submitEntityRenderState(state, scale, translation, pose, camera,
                left, top, right, bottom);
    }

    private static PlayerSkin avatarSkin(Minecraft client) {
        if ((ModConfig.get().customSkinEnabled || previewSelected) && localSkin != null) return localSkin;

        GameProfile profile = client.getGameProfile();
        if (profile == null) return null;
        java.util.UUID profileId = profile.id();
        if (accountSkinProfileId == null || !accountSkinProfileId.equals(profileId)) {
            if (accountSkinTexture != null) client.getTextureManager().release(accountSkinTexture);
            accountSkinTexture = null;
            accountSkinProfileId = profileId;
            accountSkin = null;
            accountSkinRequestInProgress = false;
            accountSkinFallbackAttempted = false;
        }
        if (!accountSkinRequestInProgress && accountSkin == null) {
            accountSkinRequestInProgress = true;
            client.getSkinManager().get(profile).whenComplete((skin, failure) -> client.execute(() -> {
                if (!profileId.equals(accountSkinProfileId)) return;
                if (failure == null && skin != null && skin.isPresent()) {
                    accountSkin = skin.get();
                    accountSkinRequestInProgress = false;
                } else if (!accountSkinFallbackAttempted) {
                    accountSkinFallbackAttempted = true;
                    loadAccountSkinByName(client, profile.name(), profileId);
                } else {
                    accountSkinRequestInProgress = false;
                    if (failure != null) {
                        System.err.println("[wvDoorClient] Could not load account skin: " + failure.getMessage());
                    }
                }
            }));
        }
        return accountSkin;
    }

    private static void loadAccountSkinByName(Minecraft client, String username, UUID profileId) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return fetchProfileSkin(username);
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new CompletionException(exception);
            }
        }, SKIN_FETCH_EXECUTOR).whenComplete((fetched, failure) -> client.execute(() -> {
            if (!profileId.equals(accountSkinProfileId)) return;
            if (failure != null) {
                accountSkinRequestInProgress = false;
                Throwable cause = failure;
                while (cause.getCause() != null) cause = cause.getCause();
                System.err.println("[wvDoorClient] Could not fetch account skin for " + username
                        + ": " + cause.getMessage());
                return;
            }
            Identifier textureId = Identifier.fromNamespaceAndPath(CrystalUtilityClient.MOD_ID,
                        "account_skin/" + profileId.toString().replace("-", ""));
            try {
                NativeImage image = NativeImage.read(fetched.png());
                if (image.getWidth() != 64 || image.getHeight() != 64) {
                    image.close();
                    throw new IOException("Downloaded account skin is not 64x64.");
                }
                DynamicTexture texture = new DynamicTexture(() -> "wvDoorClient account skin", image);
                client.getTextureManager().release(textureId);
                client.getTextureManager().register(textureId, texture);
                accountSkinTexture = textureId;
                accountSkin = PlayerSkin.insecure(dynamicTextureAsset(textureId),
                        null, null, fetched.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE);
            } catch (IOException | RuntimeException exception) {
                System.err.println("[wvDoorClient] Could not decode account skin: " + exception.getMessage());
            } finally {
                accountSkinRequestInProgress = false;
            }
        }));
    }

    public static void setSlimModel(Minecraft client, boolean slim) {
        ModConfig config = ModConfig.get();
        config.customSkinSlimModel = slim;
        ModConfig.save();
        Path selectedSkin = selectedSkinFile(ModConfig.get());
        if (selectedSkin != null) {
            try {
                registerSkin(client, selectedSkin, modelType(config));
            } catch (IOException exception) {
                status = "Could not reload the selected skin.";
            }
        }
    }

    public static void applySkin() {
        Path selectedSkin = selectedSkinFile(ModConfig.get());
        if (localSkin == null || selectedSkin == null || !Files.isRegularFile(selectedSkin)) {
            status = "Choose a valid 64x64 PNG skin first.";
            return;
        }
        ModConfig config = ModConfig.get();
        config.customSkinEnabled = true;
        config.customSkinUploadCompleted = false;
        config.customSkinUploadAllowed = true;
        ModConfig.save();
        appliedAtNanos = 0;
        uploadAttempted = false;
        status = "Skin applied locally. Official profile upload is scheduled after 10 minutes.";
    }

    public static void removeSkin(Minecraft client) {
        ModConfig config = ModConfig.get();
        config.customSkinEnabled = false;
        config.customSkinUploadCompleted = false;
        config.customSkinUploadAllowed = true;
        ModConfig.save();
        appliedAtNanos = 0;
        uploadAttempted = true;
        status = "Custom skin is no longer active on this client.";
    }

    public static void tick(Minecraft client) {
        ModConfig config = ModConfig.get();
        if (!config.customSkinEnabled || config.customSkinUploadCompleted || !config.customSkinUploadAllowed) {
            appliedAtNanos = 0;
            return;
        }
        if (client.player == null) {
            appliedAtNanos = 0;
            return;
        }
        if (appliedAtNanos == 0) appliedAtNanos = System.nanoTime();
        if (uploadAttempted || System.nanoTime() - appliedAtNanos < UPLOAD_DELAY_NANOS) return;

        uploadAttempted = true;
        status = "Checking Minecraft account ownership...";
        String accessToken = client.getUser().getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            status = "No signed-in Minecraft account. Skin remains local.";
            return;
        }
        boolean slim = config.customSkinSlimModel;
        Path skinFile = selectedSkinFile(config);
        if (skinFile == null) {
            status = "Selected skin file is missing. Skin remains local.";
            return;
        }
        UPLOAD_EXECUTOR.execute(() -> uploadAfterOwnershipCheck(accessToken, slim, skinFile));
    }

    public static PlayerSkin override(PlayerSkin current) {
        PlayerSkin selected = localSkin;
        if ((!ModConfig.get().customSkinEnabled && !previewSelected) || selected == null) return current;
        if (current == null) return selected;
        return new PlayerSkin(selected.body(), current.cape(), current.elytra(),
                selected.model(), false);
    }

    public static String countdown() {
        if (!ModConfig.get().customSkinEnabled) return status;
        if (ModConfig.get().customSkinUploadCompleted) return "Official skin already updated.";
        if (!ModConfig.get().customSkinUploadAllowed) return "Downloaded skin is local only; no profile upload.";
        if (uploadAttempted) return status;
        if (appliedAtNanos == 0) return "Waiting for a world before starting the timer.";
        long remaining = Math.max(0, UPLOAD_DELAY_NANOS - (System.nanoTime() - appliedAtNanos));
        long seconds = TimeUnit.NANOSECONDS.toSeconds(remaining);
        return String.format(java.util.Locale.ROOT,
                "Official profile update in %02d:%02d", seconds / 60, seconds % 60);
    }

    public static boolean isSlimModel() {
        return ModConfig.get().customSkinSlimModel;
    }

    private static void migrateLegacySkin(ModConfig config) throws IOException {
        if (!Files.isRegularFile(LEGACY_SKIN_FILE)) return;
        if (config.customSkinFile != null && !config.customSkinFile.isBlank()
                && Files.isRegularFile(SKIN_DIRECTORY.resolve(config.customSkinFile))) return;
        String fileName = "My Skin.png";
        Path destination = SKIN_DIRECTORY.resolve(fileName);
        if (!Files.exists(destination)) Files.copy(LEGACY_SKIN_FILE, destination);
        config.customSkinFile = fileName;
        ModConfig.save();
    }

    private static Path selectedSkinFile(ModConfig config) {
        String fileName = config.customSkinFile;
        if (fileName == null || fileName.isBlank() || !isSafeSkinName(fileName)) return null;
        Path path = SKIN_DIRECTORY.resolve(fileName).normalize();
        return path.getParent().equals(SKIN_DIRECTORY.normalize()) ? path : null;
    }

    private static boolean isSafeSkinName(String fileName) {
        return fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".png")
                && !fileName.contains("/") && !fileName.contains("\\")
                && !fileName.equals(".") && !fileName.equals("..");
    }

    private static String uniqueSkinName(String originalName) {
        String baseName = originalName.replaceAll("[^A-Za-z0-9 _.-]", "_").trim();
        if (!baseName.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) baseName += ".png";
        if (baseName.isBlank() || baseName.equalsIgnoreCase(".png")) baseName = "Skin.png";
        Path candidate = SKIN_DIRECTORY.resolve(baseName);
        int suffix = 2;
        while (Files.exists(candidate)) {
            int extensionIndex = baseName.toLowerCase(java.util.Locale.ROOT).lastIndexOf(".png");
            String stem = extensionIndex >= 0 ? baseName.substring(0, extensionIndex) : baseName;
            candidate = SKIN_DIRECTORY.resolve(stem + " (" + suffix++ + ").png");
        }
        return candidate.getFileName().toString();
    }

    private static FetchedSkin fetchProfileSkin(String username) throws IOException, InterruptedException {
        String lookupUrl = USERNAME_LOOKUP_URL + username;
        JsonObject user = getJson(lookupUrl, "Could not find that Minecraft username.");
        if (!user.has("id") || !user.has("name")) {
            throw new IOException("Mojang returned an incomplete player profile.");
        }
        String profileId = user.get("id").getAsString();
        String canonicalName = user.get("name").getAsString();
        if (!profileId.matches("[0-9a-fA-F]{32}")) {
            throw new IOException("Mojang returned an invalid player ID.");
        }

        JsonObject profile = getJson(SESSION_PROFILE_URL + profileId + "?unsigned=false",
                "Could not load that player's skin profile.");
        if (!profile.has("properties") || !profile.get("properties").isJsonArray()) {
            throw new IOException("That profile does not have skin data.");
        }
        String encodedTextures = null;
        for (JsonElement property : profile.getAsJsonArray("properties")) {
            if (property.isJsonObject() && property.getAsJsonObject().has("name")
                    && property.getAsJsonObject().get("name").getAsString().equals("textures")) {
                encodedTextures = property.getAsJsonObject().get("value").getAsString();
                break;
            }
        }
        if (encodedTextures == null) throw new IOException("That profile does not have a skin.");

        JsonObject textures;
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(encodedTextures),
                    StandardCharsets.UTF_8);
            textures = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new IOException("Mojang returned invalid skin data.");
        }
        if (textures == null || !textures.has("SKIN")) throw new IOException("That profile does not have a skin.");

        JsonObject skin = textures.getAsJsonObject("SKIN");
        java.net.URI textureUri;
        try {
            textureUri = java.net.URI.create(skin.get("url").getAsString());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new IOException("Mojang returned an invalid skin URL.");
        }
        String scheme = textureUri.getScheme();
        String texturePath = textureUri.getPath();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                || !SKIN_TEXTURE_HOST.equalsIgnoreCase(textureUri.getHost())
                || textureUri.getUserInfo() != null
                || (textureUri.getPort() != -1 && textureUri.getPort() != 80 && textureUri.getPort() != 443)
                || textureUri.getQuery() != null || textureUri.getFragment() != null
                || texturePath == null || !texturePath.matches("/texture/[0-9a-fA-F]{64}")) {
            throw new IOException("Mojang returned an unsupported skin URL.");
        }
        textureUri = java.net.URI.create("https://" + SKIN_TEXTURE_HOST + texturePath);

        byte[] png = getBytes(textureUri);
        validateSkin(png);
        JsonObject metadata = skin.has("metadata") && skin.get("metadata").isJsonObject()
                ? skin.getAsJsonObject("metadata") : null;
        boolean slim = metadata != null && metadata.has("model")
                && metadata.get("model").getAsString().equalsIgnoreCase("slim");
        return new FetchedSkin(canonicalName, slim, png);
    }

    private static JsonObject getJson(String url, String notFoundMessage) throws IOException, InterruptedException {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();
        java.net.http.HttpResponse<String> response = HTTP.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 404 || response.statusCode() == 204) throw new IOException(notFoundMessage);
        if (response.statusCode() != 200) {
            throw new IOException("Minecraft skin lookup returned HTTP " + response.statusCode() + ".");
        }
        try {
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (com.google.gson.JsonParseException | IllegalStateException exception) {
            throw new IOException("Minecraft returned an invalid skin profile.");
        }
    }

    private static byte[] getBytes(java.net.URI uri) throws IOException, InterruptedException {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "image/png")
                .GET()
                .build();
        java.net.http.HttpResponse<InputStream> response = HTTP.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("Skin image download returned HTTP " + response.statusCode() + ".");
            }
            byte[] png = body.readNBytes(1_048_577);
            if (png.length > 1_048_576) throw new IOException("Skin PNG must be 1 MiB or smaller.");
            return png;
        }
    }

    private static void validateSkin(byte[] png) throws IOException {
        try (NativeImage image = NativeImage.read(png)) {
            if (image.getWidth() != 64 || image.getHeight() != 64) {
                throw new IOException("Skin PNG must be exactly 64x64 pixels.");
            }
        }
    }

    private record FetchedSkin(String username, boolean slim, byte[] png) {}

    private static void registerSkin(Minecraft client, Path path, PlayerModelType model) throws IOException {
        NativeImage image;
        try (InputStream input = Files.newInputStream(path)) {
            image = NativeImage.read(input);
        }
        if (image.getWidth() != 64 || image.getHeight() != 64) {
            image.close();
            throw new IOException("Skin PNG must be exactly 64x64 pixels.");
        }
        client.getTextureManager().release(SKIN_TEXTURE);
        client.getTextureManager().register(SKIN_TEXTURE,
                new DynamicTexture(() -> "wvDoorClient custom skin", image));
        localSkin = PlayerSkin.insecure(dynamicTextureAsset(SKIN_TEXTURE), null, null, model);
    }

    private static ClientAsset.ResourceTexture dynamicTextureAsset(Identifier texture) {
        return new ClientAsset.ResourceTexture(texture, texture);
    }

    private static void validateSkin(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path);
             NativeImage image = NativeImage.read(input)) {
            if (image.getWidth() != 64 || image.getHeight() != 64) {
                throw new IOException("Skin PNG must be exactly 64x64 pixels.");
            }
        }
    }

    private static PlayerModelType modelType(ModConfig config) {
        return config.customSkinSlimModel ? PlayerModelType.SLIM : PlayerModelType.WIDE;
    }

    private static void uploadAfterOwnershipCheck(String accessToken, boolean slim, Path skinFile) {
        Minecraft client = Minecraft.getInstance();
        try {
            if (!ownsMinecraft(accessToken)) {
                client.execute(() -> status = "No Minecraft Java license found. Skin remains local.");
                return;
            }
            if (!ModConfig.get().customSkinEnabled || !skinFile.equals(selectedSkinFile(ModConfig.get()))) return;
            uploadOfficialSkin(accessToken, slim, skinFile);
            client.execute(() -> {
                ModConfig.get().customSkinUploadCompleted = true;
                ModConfig.save();
                status = "Official profile skin updated. Other clients may need time to refresh.";
            });
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            client.execute(() -> status = "Official skin upload failed. Check connection and account status.");
        }
    }

    private static boolean ownsMinecraft(String accessToken) throws IOException, InterruptedException {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(ENTITLEMENTS_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        java.net.http.HttpResponse<String> response = HTTP.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 401) throw new IOException("Minecraft account sign-in expired.");
        if (response.statusCode() != 200) {
            throw new IOException("Minecraft ownership check returned HTTP " + response.statusCode());
        }
        JsonObject payload;
        try {
            payload = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (com.google.gson.JsonParseException | IllegalStateException exception) {
            throw new IOException("Minecraft ownership response was invalid.");
        }
        JsonElement items = payload.get("items");
        if (items == null || !items.isJsonArray()) return false;
        for (JsonElement item : items.getAsJsonArray()) {
            if (!item.isJsonObject()) continue;
            String name = item.getAsJsonObject().has("name")
                    ? item.getAsJsonObject().get("name").getAsString() : "";
            if (name.equalsIgnoreCase("product_minecraft") || name.equalsIgnoreCase("game_minecraft")) {
                return true;
            }
        }
        return false;
    }

    private static void uploadOfficialSkin(String accessToken, boolean slim, Path skinFile)
            throws IOException, InterruptedException {
        if (Files.size(skinFile) > 1_048_576) {
            throw new IOException("Selected skin PNG exceeds the allowed size.");
        }
        byte[] png = Files.readAllBytes(skinFile);
        try (NativeImage image = NativeImage.read(png)) {
            if (image.getWidth() != 64 || image.getHeight() != 64) {
                throw new IOException("Selected skin PNG is no longer a valid 64x64 skin.");
            }
        }
        String boundary = "----wvDoorClient" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream(png.length + 512);
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"variant\"\r\n\r\n"
                + (slim ? "slim" : "classic") + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(png);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(UPLOAD_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        java.net.http.HttpResponse<Void> response = HTTP.send(request,
                java.net.http.HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Minecraft skin upload returned HTTP " + response.statusCode());
        }
    }
}
