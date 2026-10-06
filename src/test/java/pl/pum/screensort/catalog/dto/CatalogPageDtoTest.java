package pl.pum.screensort.catalog.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogPageDtoTest {

  @Test
  void keepsFilesAndPageMetadata() {
    CatalogFileDto file = exampleFile();
    CatalogPageDto page = new CatalogPageDto(List.of(file), 0, 1, 3, true);

    assertEquals(List.of(file), page.files());
    assertEquals(0, page.pageNumber());
    assertEquals(1, page.pageSize());
    assertEquals(3L, page.totalFiles());
    assertTrue(page.hasNext());
  }

  @Test
  void keepsAnIndependentReadOnlyList() {
    CatalogFileDto file = exampleFile();
    List<CatalogFileDto> originalFiles = new ArrayList<>(List.of(file));
    CatalogPageDto page = new CatalogPageDto(originalFiles, 0, 50, 1, false);

    originalFiles.clear();

    assertEquals(List.of(file), page.files());
    assertThrows(UnsupportedOperationException.class, () -> page.files().clear());
  }

  private static CatalogFileDto exampleFile() {
    Instant timestamp = Instant.parse("2026-10-06T12:00:00Z");
    return new CatalogFileDto(
        1L, 1L, "C:\\Test\\Pictures", "photo.png", "photo.png", "png",
        1024L, timestamp, "a".repeat(64), timestamp, timestamp);
  }
}
