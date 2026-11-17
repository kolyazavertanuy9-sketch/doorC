package dev.wvdoor.launcher.content;

import java.io.IOException;

public interface ContentProvider {
    ContentPlatform platform();

    ContentSearchPage search(ContentSearchQuery query) throws IOException, InterruptedException;

    ContentFile latestCompatibleFile(ContentProject project, MinecraftProfile profile)
            throws IOException, InterruptedException;
}
