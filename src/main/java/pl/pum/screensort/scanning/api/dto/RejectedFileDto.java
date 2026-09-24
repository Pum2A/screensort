package pl.pum.screensort.scanning.api.dto;

import pl.pum.screensort.scanning.image.RejectedFile;
import pl.pum.screensort.scanning.image.RejectionReason;

public record RejectedFileDto(String path, String originalName, RejectionReason reason) {
  public static RejectedFileDto from(RejectedFile source) {
    return new RejectedFileDto(source.path().toString(), source.originalName(), source.reason());
  }
}
