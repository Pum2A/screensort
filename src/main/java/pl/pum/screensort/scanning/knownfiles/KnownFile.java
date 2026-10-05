package pl.pum.screensort.scanning.knownfiles;

import java.nio.file.Path;
import java.time.Instant;

public record KnownFile(
    String relativePath, long sizeBytes, Instant lastModifiedAt, String sha256) {}
