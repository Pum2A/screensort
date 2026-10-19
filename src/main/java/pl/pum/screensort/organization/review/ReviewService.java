package pl.pum.screensort.organization.review;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pum.screensort.catalog.database.FileRecordRepository;
import pl.pum.screensort.naming.NamingService;

@Service
public class ReviewService {
  public record Request(Long analysisId, String sourceSha256, Long reviewRevision,
      Long namingRevision, String decision, String name, String category) {}

  private final FileRecordRepository files;
  private final NamingService naming;
  private final ReviewStore reviews;

  public ReviewService(FileRecordRepository files, NamingService naming, ReviewStore reviews) {
    this.files = files; this.naming = naming; this.reviews = reviews;
  }

  @Transactional(readOnly = true)
  public ReviewItem read(UUID batchId, long fileId) {
    if (fileId <= 0) throw new ReviewException(400, "INVALID_REQUEST", "Nieprawidłowe ID pliku.");
    return reviews.item(batchId, fileId).orElseThrow(() ->
        new ReviewException(404, "ITEM_NOT_FOUND", "Ten plik nie należy do wskazanej analizy folderu."));
  }

  /** The name and organization decision commit together. No source image is opened here. */
  @Transactional
  public ReviewItem save(UUID batchId, long fileId, Request request) {
    validate(fileId, request);
    // Same parent lock as the single-image naming editor, including concurrent first approvals.
    files.findForNamingById(fileId).orElseThrow(() -> new ReviewException(404, "FILE_NOT_FOUND", "Plik nie istnieje w katalogu."));
    var item = reviews.item(batchId, fileId).orElseThrow(() ->
        new ReviewException(404, "ITEM_NOT_FOUND", "Ten plik nie należy do wskazanej analizy folderu."));
    if (!"COMPLETED".equals(item.status()) || item.analysisId() == null) {
      throw new ReviewException(409, "ITEM_NOT_READY", "Poczekaj na zakończenie analizy tego obrazu.");
    }
    if (!item.analysisMatchesCatalog()) {
      throw new ReviewException(409, "SOURCE_CHANGED", "Plik zmienił się od tej analizy. Zeskanuj i przeanalizuj go ponownie.");
    }
    if (!item.analysisId().equals(request.analysisId()) || !item.sourceSha256().equals(request.sourceSha256())) {
      throw new ReviewException(409, "ANALYSIS_CHANGED", "Wynik analizy się zmienił. Odśwież formularz.");
    }
    long revision = item.review() == null ? -1 : item.review().revision();
    if (revision != request.reviewRevision() || item.namingRevision() != request.namingRevision()) {
      throw new ReviewException(409, "REVIEW_CHANGED", "Zapisano już inną decyzję. Wczytaj ją przed kolejną zmianą.");
    }
    Long approvedNamingRevision = null;
    if ("APPROVED".equals(request.decision())) {
      approvedNamingRevision = naming.approve(fileId, request.analysisId(), request.sourceSha256(),
          request.namingRevision(), request.name()).revision();
    }
    reviews.save(fileId, request.analysisId(), request.sourceSha256(), request.decision(),
        request.category(), approvedNamingRevision);
    return reviews.item(batchId, fileId).orElseThrow();
  }

  private static void validate(long fileId, Request request) {
    if (fileId <= 0 || request == null || request.analysisId() == null || request.analysisId() <= 0
        || request.sourceSha256() == null || !request.sourceSha256().matches("[0-9a-f]{64}")
        || request.reviewRevision() == null || request.reviewRevision() < -1
        || request.namingRevision() == null || request.namingRevision() < -1) {
      throw new ReviewException(400, "INVALID_REQUEST", "Nieprawidłowe dane formularza.");
    }
    if ("APPROVED".equals(request.decision())) {
      if (!ImageCategory.accepts(request.category())) {
        throw new ReviewException(422, "INVALID_CATEGORY", "Wybierz kategorię dla tego obrazu.");
      }
    } else if (!"SKIPPED".equals(request.decision()) || request.name() != null || request.category() != null) {
      throw new ReviewException(400, "INVALID_REQUEST", "Nieprawidłowa decyzja organizacji.");
    }
  }
}
