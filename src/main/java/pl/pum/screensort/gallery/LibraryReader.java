package pl.pum.screensort.gallery;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pum.screensort.organization.BatchStore;
import pl.pum.screensort.organization.review.ReviewItem;
import pl.pum.screensort.organization.review.ReviewStore;

/**
 * The whole folder for the library screen: every catalog file plus the latest analysis of it.
 * One read instead of a catalog page and a paged batch, so filters, counts and duplicates see all files.
 */
@Service
public class LibraryReader {
  /** {@code batchId} is null until the folder was analysed; then {@code items} has one row per analysed file. */
  public record Library(List<CatalogFileDto> files, UUID batchId, List<ReviewItem> items) {}

  private final CatalogReader catalog;
  private final BatchStore batches;
  private final ReviewStore reviews;

  public LibraryReader(CatalogReader catalog, BatchStore batches, ReviewStore reviews) {
    this.catalog = catalog; this.batches = batches; this.reviews = reviews;
  }

  @Transactional(readOnly = true)
  public Library read(String folderPath) throws IOException {
    List<CatalogFileDto> files = catalog.read(folderPath);
    if (files.isEmpty()) return new Library(files, null, List.of());
    // sourceFolder is the canonical root path, the same key the batch is stored under.
    return batches.latest(files.getFirst().sourceFolder())
        .map(batch -> new Library(files, batch.id(), reviews.all(batch.id())))
        .orElseGet(() -> new Library(files, null, List.of()));
  }
}
