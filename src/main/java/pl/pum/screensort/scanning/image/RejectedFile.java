package pl.pum.screensort.scanning.image;

import java.nio.file.Path;

public record RejectedFile(Path path, String originalName, RejectionReason reason) {
}
