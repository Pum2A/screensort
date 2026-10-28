package pl.pum.screensort.gallery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.pum.screensort.analysis.BatchStore;
import pl.pum.screensort.review.ReviewItem;
import pl.pum.screensort.review.ReviewStore;

class LibraryReaderTest {
  private final CatalogReader catalog = mock(CatalogReader.class);
  private final BatchStore batches = mock(BatchStore.class);
  private final ReviewStore reviews = mock(ReviewStore.class);
  private final LibraryReader reader = new LibraryReader(catalog, batches, reviews);

  private static CatalogFileDto file(long id) {
    Instant now = Instant.parse("2026-10-25T12:00:00Z");
    return new CatalogFileDto(id, 1L, "C:\root", "a.png", "a.png", "png", 10, now, "a".repeat(64), now, now);
  }

  @Test
  void emptyCatalogSkipsTheAnalysisLookup() throws Exception {
    when(catalog.read("folder")).thenReturn(List.of());

    assertThat(reader.read("folder")).isEqualTo(new LibraryReader.Library(List.of(), null, List.of()));
    verifyNoInteractions(batches, reviews);
  }

  @Test
  void folderWithoutAnalysisHasNoBatch() throws Exception {
    when(catalog.read("folder")).thenReturn(List.of(file(1)));
    when(batches.latest("C:\root")).thenReturn(Optional.empty());

    var library = reader.read("folder");

    assertThat(library.files()).hasSize(1);
    assertThat(library.batchId()).isNull();
    assertThat(library.items()).isEmpty();
  }

  @Test
  void latestBatchOfTheCanonicalRootIsReturnedWithAllItems() throws Exception {
    UUID batchId = UUID.randomUUID();
    var job = new BatchStore.Job(batchId, "C:\root", "COMPLETED", true, true, 1, 1, 0, 0, Instant.now());
    var item = new ReviewItem(1, "a.png", "COMPLETED", "GAMES", "lobby.png", false, null, 7L,
        "a".repeat(64), true, -1, null, null);
    when(catalog.read("folder")).thenReturn(List.of(file(1)));
    when(batches.latest("C:\root")).thenReturn(Optional.of(job));
    when(reviews.all(batchId)).thenReturn(List.of(item));

    var library = reader.read("folder");

    assertThat(library.batchId()).isEqualTo(batchId);
    assertThat(library.items()).containsExactly(item);
  }
}
