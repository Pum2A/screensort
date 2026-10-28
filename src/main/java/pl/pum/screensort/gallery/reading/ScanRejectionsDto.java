package pl.pum.screensort.gallery.reading;

import java.util.List;

/** Ograniczona próbka, a nie druga pełna lista wyników skanowania. */
public record ScanRejectionsDto(List<RejectedFileDto> files, int totalRejected, boolean truncated) {
  public ScanRejectionsDto {
    files = List.copyOf(files);
  }
}
