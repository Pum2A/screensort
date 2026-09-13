package pl.pum.screensort.scanning;

import java.nio.file.Path;
import java.time.Instant;

public record ScannedImage(Path path, String originalName, String extension, long sizeBytes, Instant lastModifiedAt) {

}
