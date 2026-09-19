package pl.pum.screensort.scanning.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ScanRequest(@NotBlank String folderPath) {}
