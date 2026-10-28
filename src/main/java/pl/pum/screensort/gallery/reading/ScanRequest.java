package pl.pum.screensort.gallery.reading;

import jakarta.validation.constraints.NotBlank;

public record ScanRequest(@NotBlank String folderPath) {}
