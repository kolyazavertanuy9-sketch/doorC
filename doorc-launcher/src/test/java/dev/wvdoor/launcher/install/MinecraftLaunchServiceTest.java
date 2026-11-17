package dev.wvdoor.launcher.install;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.wvdoor.launcher.auth.GameAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftLaunchServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void launchesInstalledProfileWithAccountArguments() throws Exception {
        Path gameDirectory = temporaryDirectory.resolve("game");
        Path instance = gameDirectory.resolve("instances/DoorC-1.21.11");
        Path versions = instance.resolve("versions");
        Path baseVersion = versions.resolve("1.21.11");
        Path fabricVersion = versions.resolve("fabric-loader-0.19.5-1.21.11");
        Path libraries = instance.resolve("libraries/test");
        Files.createDirectories(baseVersion);
        Files.createDirectories(fabricVersion);
        Files.createDirectories(libraries);
        Files.createDirectories(instance.resolve("natives/1.21.11"));
        Files.createFile(baseVersion.resolve("1.21.11.jar"));
        writeProbeJar(libraries.resolve("probe.jar"));
        writeBaseMetadata(baseVersion.resolve("1.21.11.json"));
        writeFabricMetadata(fabricVersion.resolve("fabric-loader-0.19.5-1.21.11.json"));

        GameAccount account = GameAccount.offline("TestPlayer");
        Process process = new MinecraftLaunchService().launchDoorC(gameDirectory, account);
        assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Game process did not finish its launch probe.");
        assertTrue(process.exitValue() == 0, "Launch probe failed: "
                + Files.readString(instance.resolve("logs/doorc-launcher.log")));

        String arguments = Files.readString(instance.resolve("doorc-launch-probe.out"));
        assertTrue(arguments.contains("--username\nTestPlayer\n"));
        assertTrue(arguments.contains("--uuid\n" + account.uuid() + "\n"));
        assertTrue(arguments.contains("--accessToken\n0\n"));
        assertTrue(arguments.contains("--gameDir\n" + instance.toAbsolutePath() + "\n"));
    }

    private static void writeBaseMetadata(Path path) throws Exception {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("id", "1.21.11");
        metadata.addProperty("mainClass", LaunchProbe.class.getName());
        JsonObject assetIndex = new JsonObject();
        assetIndex.addProperty("id", "1.21.11");
        metadata.add("assetIndex", assetIndex);
        metadata.add("libraries", new JsonArray());

        JsonObject arguments = new JsonObject();
        JsonArray gameArguments = new JsonArray();
        for (String value : new String[]{"--username", "${auth_player_name}", "--uuid", "${auth_uuid}",
                "--accessToken", "${auth_access_token}", "--gameDir", "${game_directory}"}) {
            gameArguments.add(value);
        }
        JsonArray jvmArguments = new JsonArray();
        jvmArguments.add("-cp");
        jvmArguments.add("${classpath}");
        jvmArguments.add("-Djava.library.path=${natives_directory}");
        arguments.add("game", gameArguments);
        arguments.add("jvm", jvmArguments);
        metadata.add("arguments", arguments);
        Files.writeString(path, metadata.toString());
    }

    private static void writeFabricMetadata(Path path) throws Exception {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("id", "fabric-loader-0.19.5-1.21.11");
        metadata.addProperty("inheritsFrom", "1.21.11");
        metadata.addProperty("mainClass", LaunchProbe.class.getName());
        JsonArray libraries = new JsonArray();
        JsonObject probeLibrary = new JsonObject();
        JsonObject downloads = new JsonObject();
        JsonObject artifact = new JsonObject();
        artifact.addProperty("path", "test/probe.jar");
        downloads.add("artifact", artifact);
        probeLibrary.add("downloads", downloads);
        libraries.add(probeLibrary);
        metadata.add("libraries", libraries);
        Files.writeString(path, metadata.toString());
    }

    private static void writeProbeJar(Path path) throws Exception {
        try (InputStream classFile = LaunchProbe.class.getResourceAsStream("LaunchProbe.class");
             JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            if (classFile == null) throw new IllegalStateException("Launch probe class is missing.");
            jar.putNextEntry(new JarEntry("dev/wvdoor/launcher/install/LaunchProbe.class"));
            classFile.transferTo(jar);
            jar.closeEntry();
        }
    }
}
