package pl.pum.screensort.scanning.result;

import java.nio.file.Path;

public record RejectedFile(Path path, String originalName, RejectionReason reason) {
}
