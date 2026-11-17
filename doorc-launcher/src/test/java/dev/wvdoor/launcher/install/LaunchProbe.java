package dev.wvdoor.launcher.install;

import java.nio.file.Files;
import java.nio.file.Path;

public final class LaunchProbe {
    private LaunchProbe() {}

    public static void main(String[] arguments) throws Exception {
        Files.writeString(Path.of("doorc-launch-probe.out"), String.join("\n", arguments) + "\n");
    }
}
