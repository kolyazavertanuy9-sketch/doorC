package dev.crystalutility.media;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MediaController {
    public enum Command { TOGGLE, NEXT, PREVIOUS }

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CrystalUtility-Media");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile MediaSnapshot snapshot = MediaSnapshot.empty();
    private static volatile MediaSnapshot previousSnapshot = MediaSnapshot.empty();
    private static volatile String lastTrackKey = "";
    private static volatile long lastProviderPositionMillis = -1;
    private static volatile long trackChangedAt;
    private static volatile String lastArtworkDiagnostic = "";
    private static volatile boolean started;

    private MediaController() {}

    public static void start() {
        if (started) return;
        started = true;
        EXECUTOR.scheduleWithFixedDelay(MediaController::poll, 0, 2, TimeUnit.SECONDS);
    }

    public static MediaSnapshot snapshot() {
        MediaSnapshot current = snapshot;
        long elapsed = Math.max(0L, System.currentTimeMillis() - current.updatedAtMillis());
        long estimatedPosition = current.positionMillis() + elapsed;
        long position = current.playing()
                ? current.durationMillis() > 0 ? Math.min(current.durationMillis(), estimatedPosition) : estimatedPosition
                : current.positionMillis();
        return new MediaSnapshot(current.title(), current.artist(), position, current.durationMillis(),
                current.playing(), current.updatedAtMillis(), current.artworkBase64());
    }

    public static MediaSnapshot displaySnapshot() {
        MediaSnapshot current = snapshot();
        if (trackAgeMillis() < 180 && previousSnapshot.hasTrack()) {
            MediaSnapshot previous = previousSnapshot;
            return new MediaSnapshot(previous.title(), previous.artist(), previous.positionMillis(),
                    previous.durationMillis(), previous.playing(), previous.updatedAtMillis(), previous.artworkBase64());
        }
        return current;
    }

    public static long trackAgeMillis() {
        return trackChangedAt == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - trackChangedAt;
    }

    public static boolean previousTrackHasTrack() {
        return previousSnapshot.hasTrack();
    }

    public static void send(Command command) {
        EXECUTOR.execute(() -> {
            try {
                switch (platform()) {
                    case "windows" -> run("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                            windowsCommand(command));
                    case "linux" -> run("playerctl", linuxCommand(command));
                    case "mac" -> run("osascript", "-e", macCommand(command));
                    default -> throw new IllegalStateException("Unsupported operating system: " + System.getProperty("os.name"));
                }
            } catch (Exception exception) {
                System.err.println("[wvDoorClient] Media command failed: " + exception.getMessage());
            }
        });
    }

    private static void poll() {
        try {
            String os = platform();
            MediaSnapshot updated = switch (os) {
                case "windows" -> pollWindows();
                case "linux" -> pollLinux();
                case "mac" -> pollMac();
                default -> MediaSnapshot.empty();
            };
            updated = normalizeSnapshot(updated);
            String trackKey = updated.title() + '\u0000' + updated.artist();
            long now = System.currentTimeMillis();
            boolean sameTrack = trackKey.equals(lastTrackKey);
            if (!sameTrack) {
                previousSnapshot = snapshot;
                lastTrackKey = trackKey;
                trackChangedAt = System.currentTimeMillis();
                lastProviderPositionMillis = updated.positionMillis();
            } else {
                updated = reconcilePosition(updated, snapshot(), now);
            }
            String artwork = updated.artworkBase64();
            if ((artwork == null || artwork.isBlank()) && sameTrack) {
                artwork = snapshot.artworkBase64();
            }
            if (updated.artworkBase64() != null && !updated.artworkBase64().isBlank()) {
                lastArtworkDiagnostic = "";
            }
            snapshot = new MediaSnapshot(updated.title(), updated.artist(), updated.positionMillis(),
                    updated.durationMillis(), updated.playing(), now, artwork);
        } catch (Exception exception) {
            System.err.println("[wvDoorClient] Media polling failed: " + exception.getMessage());
            snapshot = MediaSnapshot.empty();
        }
    }

    private static MediaSnapshot normalizeSnapshot(MediaSnapshot media) {
        long duration = Math.max(0L, media.durationMillis());
        long position = Math.max(0L, media.positionMillis());
        if (duration > 0) position = Math.min(position, duration);
        return new MediaSnapshot(media.title(), media.artist(), position, duration,
                media.playing(), media.updatedAtMillis(), media.artworkBase64());
    }

    private static MediaSnapshot reconcilePosition(MediaSnapshot updated, MediaSnapshot estimated, long now) {
        long reported = updated.positionMillis();
        long position = reported;
        if (updated.playing() && estimated.playing() && lastProviderPositionMillis >= 0) {
            long providerDelta = reported - lastProviderPositionMillis;
            if (providerDelta < -1_000) {
                position = reported;
            } else if (providerDelta <= 250) {
                position = estimated.positionMillis();
            } else {
                position = Math.max(reported, estimated.positionMillis() - 500);
            }
        }
        lastProviderPositionMillis = reported;
        long duration = Math.max(0L, updated.durationMillis());
        position = Math.max(0L, duration > 0 ? Math.min(position, duration) : position);
        return new MediaSnapshot(updated.title(), updated.artist(), position, duration,
                updated.playing(), now, updated.artworkBase64());
    }

    private static MediaSnapshot pollLinux() throws Exception {
        String output = run("playerctl", "metadata", "--format",
                "{{title}}\\t{{artist}}\\t{{position}}\\t{{mpris:length}}\\t{{status}}");
        String[] fields = output.strip().split("\\t", -1);
        if (fields.length < 5) return MediaSnapshot.empty();
        return new MediaSnapshot(fields[0], fields[1], parseSeconds(fields[2]),
                parseLong(fields[3]) / 1000, "Playing".equalsIgnoreCase(fields[4]), System.currentTimeMillis());
    }

    private static MediaSnapshot pollMac() throws Exception {
        String script = "tell application \"System Events\" to set appNames to name of (every process whose background only is false)\n"
                + "set resultText to \"\"\n"
                + "repeat with appName in appNames\n"
                + "try\n"
                + "set appName to contents of appName\n"
                + "if appName is \"Spotify\" then\n"
                + "tell application \"Spotify\" to set resultText to (name of current track) & \"\\t\" & (artist of current track) & \"\\t\" & (player position as string) & \"\\t\" & ((duration of current track) / 1000 as string) & \"\\t\" & (player state as string)\n"
                + "exit repeat\n"
                + "end if\n"
                + "end try\n"
                + "end repeat\n"
                + "return resultText";
        String output = run("osascript", "-e", script);
        String[] fields = output.strip().split("\\t", -1);
        if (fields.length < 5) return MediaSnapshot.empty();
        return new MediaSnapshot(fields[0], fields[1], parseLong(fields[2]) * 1000,
                parseLong(fields[3]) * 1000, "playing".equalsIgnoreCase(fields[4]), System.currentTimeMillis());
    }

    private static MediaSnapshot pollWindows() throws Exception {
        String script = "/assets/crystalutility/media-windows.ps1";
        try (var stream = MediaController.class.getResourceAsStream(script)) {
            if (stream == null) throw new IllegalStateException("Missing Windows media bridge resource");
            String ps = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String encoded = Base64.getEncoder().encodeToString(ps.getBytes(StandardCharsets.UTF_16LE));
            String output = run("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
            String[] fields = output.strip().split("\\|", -1);
            if (fields.length < 5) return MediaSnapshot.empty();
            String title = new String(Base64.getDecoder().decode(fields[0]), StandardCharsets.UTF_8);
            String artist = new String(Base64.getDecoder().decode(fields[1]), StandardCharsets.UTF_8);
            String artwork = fields.length > 5 ? fields[5] : "";
            if (artwork.isBlank() && fields.length > 6) {
                logArtworkDiagnostic(fields[6]);
            }
            return new MediaSnapshot(title, artist, parseLong(fields[2]), parseLong(fields[3]),
                    "Playing".equalsIgnoreCase(fields[4]), System.currentTimeMillis(), artwork);
        }
    }

    private static void logArtworkDiagnostic(String diagnostic) {
        if (diagnostic == null || diagnostic.isBlank() || diagnostic.equals(lastArtworkDiagnostic)) return;
        lastArtworkDiagnostic = diagnostic;
        System.err.println("[wvDoorClient] Media artwork source: " + diagnostic);
    }

    private static String windowsCommand(Command command) {
        String method = switch (command) {
            case TOGGLE -> "TogglePlayPause";
            case NEXT -> "SkipNext";
            case PREVIOUS -> "SkipPrevious";
        };
        return "$ErrorActionPreference='Stop'; Add-Type -AssemblyName System.Runtime.WindowsRuntime; "
                + "$t=[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]; "
                + "$s=[System.WindowsRuntimeSystemExtensions].GetMethods()|?{$_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1}|select -First 1; "
                + "$operation=[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync(); "
                + "$task=$s.MakeGenericMethod($t).Invoke($null,@($operation)); "
                + "$m=$task.GetAwaiter().GetResult(); $session=$m.GetCurrentSession(); "
                + "if($null -ne $session){$op=$session.Try" + method + "Async(); "
                + "$boolTask=([System.WindowsRuntimeSystemExtensions].GetMethods()|?{$_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetGenericArguments().Count -eq 1 -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'}|select -First 1)"
                + ".MakeGenericMethod([bool]).Invoke($null,@($op)); $boolTask.GetAwaiter().GetResult()|Out-Null}";
    }

    private static String linuxCommand(Command command) {
        return switch (command) {
            case TOGGLE -> "play-pause";
            case NEXT -> "next";
            case PREVIOUS -> "previous";
        };
    }

    private static String macCommand(Command command) {
        String verb = switch (command) {
            case TOGGLE -> "playpause";
            case NEXT -> "next track";
            case PREVIOUS -> "previous track";
        };
        return "tell application \"Spotify\" to " + verb;
    }

    private static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "mac";
        if (os.contains("linux") || os.contains("unix")) return "linux";
        return "unsupported";
    }

    private static String run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).start();
        CompletableFuture<String> stdout = readAsync(process.getInputStream());
        CompletableFuture<String> stderr = readAsync(process.getErrorStream());
        boolean completed = process.waitFor(8000, TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor();
            stdout.cancel(true);
            stderr.cancel(true);
            throw new IllegalStateException("Command timed out after 8 seconds: " + command[0]);
        }
        String output = stdout.get(1, TimeUnit.SECONDS);
        String errorOutput = stderr.get(1, TimeUnit.SECONDS);
        if (process.exitValue() != 0) {
            throw new IllegalStateException(command[0] + " exited with code " + process.exitValue()
                    + (errorOutput.isBlank() ? "" : ": " + errorOutput.strip())
                    + (output.isBlank() ? "" : System.lineSeparator() + output.strip()));
        }
        return output.stripTrailing();
    }

    private static CompletableFuture<String> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value.strip()); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    private static long parseSeconds(String value) {
        try { return (long) (Double.parseDouble(value.strip()) * 1000); }
        catch (NumberFormatException ignored) { return 0L; }
    }
}
