package dev.wvdoor.launcher.content;

public record ContentProject(ContentPlatform platform, ContentType type, String id,
                             String title, String author, String summary, String projectUrl,
                             String iconUrl, long downloads, boolean downloadAllowed) {
    public ContentProject {
        if (platform == null || type == null) throw new IllegalArgumentException("Project type is required.");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Project id is required.");
        title = title == null || title.isBlank() ? "Untitled project" : title;
        author = author == null ? "Unknown author" : author;
        summary = summary == null ? "" : summary;
        projectUrl = projectUrl == null ? "" : projectUrl;
        iconUrl = iconUrl == null ? "" : iconUrl;
    }

    @Override
    public String toString() {
        return title + "  ·  " + author + "  ·  " + downloads + " downloads";
    }
}
