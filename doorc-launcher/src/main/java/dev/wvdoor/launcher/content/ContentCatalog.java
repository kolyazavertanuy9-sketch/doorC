package dev.wvdoor.launcher.content;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

public final class ContentCatalog {
    private final Map<ContentPlatform, ContentProvider> providers;

    public ContentCatalog() {
        EnumMap<ContentPlatform, ContentProvider> configured = new EnumMap<>(ContentPlatform.class);
        configured.put(ContentPlatform.MODRINTH, new ModrinthApiClient());
        configured.put(ContentPlatform.CURSEFORGE, new CurseForgeApiClient());
        providers = Map.copyOf(configured);
    }

    public ContentSearchPage search(ContentPlatform platform, ContentSearchQuery query)
            throws IOException, InterruptedException {
        return provider(platform).search(query);
    }

    public ContentFile latestCompatibleFile(ContentProject project, MinecraftProfile profile)
            throws IOException, InterruptedException {
        return provider(project.platform()).latestCompatibleFile(project, profile);
    }

    private ContentProvider provider(ContentPlatform platform) throws IOException {
        ContentProvider provider = providers.get(platform);
        if (provider == null) throw new IOException("Unsupported content provider: " + platform);
        return provider;
    }
}
