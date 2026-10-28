package pl.pum.screensort.review;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.pum.screensort.gallery.FileRecordEntity;
import pl.pum.screensort.gallery.FileRecordRepository;
import pl.pum.screensort.suggestions.NamingService;

class ReviewServiceTest {
  private static final String HASH = "a".repeat(64);
  private final UUID batchId = UUID.randomUUID();
  private final FileRecordRepository files = mock(FileRecordRepository.class);
  private final NamingService naming = mock(NamingService.class);
  private final ReviewStore store = mock(ReviewStore.class);
  private final ReviewService service = new ReviewService(files, naming, store);

  @BeforeEach void prepare() {
    when(files.findForNamingById(1)).thenReturn(Optional.of(mock(FileRecordEntity.class)));
    when(store.item(batchId, 1)).thenReturn(Optional.of(item("COMPLETED", true, -1, null)));
  }

  @Test void skipDoesNotRenameOrDeleteAnExistingName() {
    service.save(batchId, 1, request("SKIPPED", null, null));
    verify(store).save(1, 8, HASH, "SKIPPED", null, null);
    verifyNoInteractions(naming);
  }

  @Test void rejectsMissingVersionsUnknownCategoriesAndWrongDecisionShapes() {
    failure(new ReviewService.Request(8L, HASH, null, -1L, "APPROVED", "mug.png", "OTHER"), "INVALID_REQUEST");
    failure(new ReviewService.Request(8L, HASH, -1L, null, "APPROVED", "mug.png", "OTHER"), "INVALID_REQUEST");
    failure(request("APPROVED", "mug.png", "REVIEW"), "INVALID_CATEGORY");
    failure(request("APPROVED", "mug.png", "__proto__"), "INVALID_CATEGORY");
    failure(request("SKIPPED", "mug.png", null), "INVALID_REQUEST");
    failure(request("DELETE", null, null), "INVALID_REQUEST");
    verify(store, never()).save(anyLong(), anyLong(), any(), any(), any(), any());
    verifyNoInteractions(naming);
  }

  @Test void rejectsAnotherBatchPendingItemsAndOutdatedAnalysis() {
    when(store.item(batchId, 1)).thenReturn(Optional.empty());
    failure(request("SKIPPED", null, null), "ITEM_NOT_FOUND");
    when(store.item(batchId, 1)).thenReturn(Optional.of(item("PENDING", true, -1, null)));
    failure(request("SKIPPED", null, null), "ITEM_NOT_READY");
    when(store.item(batchId, 1)).thenReturn(Optional.of(item("COMPLETED", false, -1, null)));
    failure(request("SKIPPED", null, null), "SOURCE_CHANGED");
    when(store.item(batchId, 1)).thenReturn(Optional.of(item("COMPLETED", true, -1, null)));
    failure(new ReviewService.Request(9L, HASH, -1L, -1L, "SKIPPED", null, null), "ANALYSIS_CHANGED");
    verify(store, never()).save(anyLong(), anyLong(), any(), any(), any(), any());
  }

  @Test void comparesTheNamingVersionEvenForSkipping() {
    when(store.item(batchId, 1)).thenReturn(Optional.of(item("COMPLETED", true, 2, null)));
    failure(request("SKIPPED", null, null), "REVIEW_CHANGED");
  }

  private void failure(ReviewService.Request request, String code) {
    assertThatThrownBy(() -> service.save(batchId, 1, request)).isInstanceOfSatisfying(ReviewException.class,
        error -> assertThat(error.code()).isEqualTo(code));
  }
  private static ReviewService.Request request(String decision, String name, String category) {
    return new ReviewService.Request(8L, HASH, -1L, -1L, decision, name, category);
  }
  static ReviewItem item(String status, boolean matches, long namingRevision, ReviewItem.Decision review) {
    return new ReviewItem(1, "IMG_0026.png", status, "PHOTOGRAPHS", "mug.png", false, null,
        8L, HASH, matches, namingRevision, null, review);
  }
}
