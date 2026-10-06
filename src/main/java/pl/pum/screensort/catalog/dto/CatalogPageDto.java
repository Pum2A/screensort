package pl.pum.screensort.catalog.dto;

import java.util.List;

/** Jedna strona opisów plików. Numeracja stron zaczyna się od 0. */
public record CatalogPageDto(
    List<CatalogFileDto> files,
    int pageNumber,
    int pageSize,
    long totalFiles,
    boolean hasNext) {
  public CatalogPageDto {
    files = List.copyOf(files);
  }
}
