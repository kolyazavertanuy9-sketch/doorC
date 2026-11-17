package dev.wvdoor.launcher.content;

public enum ContentPlatform {
    MODRINTH("Modrinth"),
    CURSEFORGE("CurseForge");

    private final String displayName;

    ContentPlatform(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
