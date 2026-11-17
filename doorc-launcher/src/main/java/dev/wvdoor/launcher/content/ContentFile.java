package dev.wvdoor.launcher.content;

import java.net.URI;

public record ContentFile(ContentPlatform platform, ContentType type, String projectId,
                          String versionId, String versionName, String fileName, URI downloadUri,
                          long size, String hashAlgorithm, String hash) {
    public ContentFile {
        if (platform == null || type == null) throw new IllegalArgumentException("File type is required.");
        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("Project id is required.");
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("File name is required.");
        if (downloadUri == null || !"https".equalsIgnoreCase(downloadUri.getScheme())) {
            throw new IllegalArgumentException("A secure HTTPS download URL is required.");
        }
        versionId = versionId == null ? "" : versionId;
        versionName = versionName == null ? "" : versionName;
        hashAlgorithm = hashAlgorithm == null ? "" : hashAlgorithm;
        hash = hash == null ? "" : hash;
    }
}
