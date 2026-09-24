package pl.pum.screensort.scanning.api.dto;

import pl.pum.screensort.scanning.image.AcceptedImage;

public record AcceptedImageDto(String path, String originalName, String extension, long sizeBytes) {
  public static AcceptedImageDto from(AcceptedImage source) {
    return new AcceptedImageDto(
        source.path().toString(), source.originalName(), source.extension(), source.sizeBytes());
  }
}
