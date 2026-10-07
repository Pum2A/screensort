package pl.pum.screensort.scanning.api.dto;

import java.util.List;

/** Ograniczona próbka, a nie druga pełna lista wyników skanowania. */
public record ScanRejectionsDto(List<RejectedFileDto> files, int totalRejected, boolean truncated) {
  public ScanRejectionsDto {
    files = List.copyOf(files);
  }
}
