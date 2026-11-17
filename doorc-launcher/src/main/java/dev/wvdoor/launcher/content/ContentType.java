package dev.wvdoor.launcher.content;

public enum ContentType {
    MODS("Mods", "mod", "Mods"),
    MAPS("Maps", null, "Worlds"),
    RESOURCE_PACKS("Texture packs", "resourcepack", "Resource Packs");

    private final String displayName;
    private final String modrinthProjectType;
    private final String curseForgeClassName;

    ContentType(String displayName, String modrinthProjectType, String curseForgeClassName) {
        this.displayName = displayName;
        this.modrinthProjectType = modrinthProjectType;
        this.curseForgeClassName = curseForgeClassName;
    }

    public String displayName() {
        return displayName;
    }

    String modrinthProjectType() {
        return modrinthProjectType;
    }

    String curseForgeClassName() {
        return curseForgeClassName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
