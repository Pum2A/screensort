package pl.pum.screensort.scanning.domain;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Read-only metadata collected for a supported image inside an allowed root.
 */
public record DiscoveredImage(
        Path canonicalPath,
        Path relativePath,
        ImageFormat format,
        long sizeBytes,
        Instant lastModifiedAt) {

    public DiscoveredImage {
        canonicalPath = Objects.requireNonNull(canonicalPath, "canonicalPath must not be null").normalize();
        relativePath = Objects.requireNonNull(relativePath, "relativePath must not be null").normalize();
        format = Objects.requireNonNull(format, "format must not be null");
        lastModifiedAt = Objects.requireNonNull(lastModifiedAt, "lastModifiedAt must not be null");

        if (!canonicalPath.isAbsolute()) {
            throw new IllegalArgumentException("canonicalPath must be absolute");
        }
        if (relativePath.isAbsolute() || relativePath.startsWith("..")) {
            throw new IllegalArgumentException("relativePath must stay inside the allowed root");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
    }

    public String originalName() {
        return this.relativePath.getFileName().toString();
    }

    public String mediaType() {
        return this.format.mediaType();
    }

}
