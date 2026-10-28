package pl.pum.screensort.gallery.reading;

import java.nio.file.Path;

public record RejectedFile(Path path, String originalName, RejectionReason reason) {
}
