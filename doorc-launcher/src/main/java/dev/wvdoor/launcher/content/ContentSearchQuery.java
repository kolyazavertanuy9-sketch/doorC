package dev.wvdoor.launcher.content;

public record ContentSearchQuery(ContentType type, String query, MinecraftProfile profile,
                                 int offset, int limit) {
    public ContentSearchQuery {
        if (type == null || profile == null) throw new IllegalArgumentException("Content type and profile are required.");
        query = query == null ? "" : query.strip();
        if (offset < 0 || limit < 1 || limit > 50) {
            throw new IllegalArgumentException("Search pagination is out of range.");
        }
    }
}
