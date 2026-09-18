package pl.pum.screensort.scanning.web;

import jakarta.validation.constraints.NotBlank;

public record ScanRequest(@NotBlank String folderPath) {}
