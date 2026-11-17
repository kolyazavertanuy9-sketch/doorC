package dev.crystalutility.media;

public record MediaSnapshot(String title, String artist, long positionMillis, long durationMillis,
                            boolean playing, long updatedAtMillis, String artworkBase64) {
    public MediaSnapshot(String title, String artist, long positionMillis, long durationMillis,
                         boolean playing, long updatedAtMillis) {
        this(title, artist, positionMillis, durationMillis, playing, updatedAtMillis, "");
    }

    public static MediaSnapshot empty() {
        return new MediaSnapshot("", "", 0L, 0L, false, System.currentTimeMillis(), "");
    }

    public boolean hasTrack() {
        return !title.isBlank() || !artist.isBlank();
    }
}
