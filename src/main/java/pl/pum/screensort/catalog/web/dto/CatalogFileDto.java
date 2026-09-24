package pl.pum.screensort.catalog.web.dto;

import java.time.Instant;
import pl.pum.screensort.catalog.database.FileRecordEntity;

public record CatalogFileDto(
    Long id,
    Long allowedRootId,
    String sourceFolder,
    String relativePath,
    String originalName,
    String extension,
    long sizeBytes,
    Instant lastModifiedAt,
    String sha256,
    Instant firstDiscoveredAt,
    Instant lastSeenAt) {
  public static CatalogFileDto from(FileRecordEntity entity) {
    return new CatalogFileDto(
        entity.getId(),
        entity.getAllowedRoot().getId(),
        entity.getAllowedRoot().getCanonicalPath(),
        entity.getRelativePath(),
        entity.getOriginalName(),
        entity.getExtension(),
        entity.getSizeBytes(),
        entity.getLastModifiedAt(),
        entity.getSha256(),
        entity.getFirstDiscoveredAt(),
        entity.getLastSeenAt());
  }
}
