package dev.wvdoor.launcher.content;

import java.util.List;

public record ContentSearchPage(List<ContentProject> projects, long totalHits, int offset, int limit) {
    public ContentSearchPage {
        projects = List.copyOf(projects);
        if (totalHits < 0 || offset < 0 || limit < 1) {
            throw new IllegalArgumentException("Invalid search page metadata.");
        }
    }

    public boolean hasMore() {
        return offset + projects.size() < totalHits;
    }
}
