package pl.pum.screensort.scanning.image;

import java.nio.file.Path;
import java.time.Instant;

public record KnownFile(
    String relativePath, long sizeBytes, Instant lastModifiedAt, String sha256) {}
