package pl.pum.screensort.scanning.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ScanRequest(@NotBlank String folderPath) {}
