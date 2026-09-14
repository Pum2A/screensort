package pl.pum.screensort.scanning.image;

import java.nio.file.Path;
import java.time.Instant;

public record ScannedImage(Path path, String originalName, String extension, long sizeBytes, Instant lastModifiedAt) {
    private static final long KILOBYTE = 1024L;
    private static final long MEGABYTE = KILOBYTE * 1024;
    private static final long GIGABYTE = MEGABYTE * 1024;
    public String formattedSize() {
        if (sizeBytes >= GIGABYTE) {
            double size = (double) sizeBytes / GIGABYTE;
            return String.format("%.2f GB", size);
        }

        if (sizeBytes >= MEGABYTE) {
            double size = (double) sizeBytes / MEGABYTE;
            return String.format("%.2f MB", size);
        }

        if (sizeBytes >= KILOBYTE) {
            double size = (double) sizeBytes / KILOBYTE;
            return String.format("%.2f KB", size);
        }

        return sizeBytes + " B";
    }

}
