package pl.pum.screensort.naming;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pum.screensort.analysis.database.AnalysisStore;
import pl.pum.screensort.analysis.result.AnalysisResult;
import pl.pum.screensort.analysis.result.SavedAnalysis;
import pl.pum.screensort.catalog.database.FileRecordEntity;
import pl.pum.screensort.catalog.database.FileRecordRepository;
import pl.pum.screensort.naming.api.dto.NamingReviewDto;
import pl.pum.screensort.naming.database.FileNamingEntity;
import pl.pum.screensort.naming.database.FileNamingRepository;

/** Reads saved analysis and stores a user's decision. Never opens or renames source images. */
@Service
public class NamingService {
  private final FileRecordRepository files;
  private final AnalysisStore analyses;
  private final FileNamingRepository names;
  private final NameSuggestionGenerator generator;

  public NamingService(FileRecordRepository files, AnalysisStore analyses,
      FileNamingRepository names, NameSuggestionGenerator generator) {
    this.files = files;
    this.analyses = analyses;
    this.names = names;
    this.generator = generator;
  }

  @Transactional(readOnly = true)
  public NamingReviewDto read(long fileId) {
    validateId(fileId);
    var file = files.findById(fileId).orElseThrow(NamingService::missingFile);
    return dto(file, analysis(fileId), names.findByFile_Id(fileId).orElse(null));
  }

  @Transactional
  public NamingReviewDto approve(long fileId, long analysisId, String sourceSha256,
      long revision, String name) {
    validateId(fileId);
    if (analysisId <= 0 || sourceSha256 == null || !sourceSha256.matches("[0-9a-f]{64}")
        || revision < -1) {
      throw new NamingException(400, "INVALID_REQUEST", "Nieprawidłowe dane zatwierdzenia.");
    }
    // Lock the parent even before the first naming row exists: two requests cannot both create it.
    var file = files.findForNamingById(fileId).orElseThrow(NamingService::missingFile);
    var analysis = analysis(fileId);
    if (!analysis.result().sourceSha256().equals(file.getSha256())) {
      throw new NamingException(409, "ANALYSIS_OUTDATED", "Wykonaj analizę aktualnej wersji obrazu.");
    }
    if (analysis.id() != analysisId || !analysis.result().sourceSha256().equals(sourceSha256)) {
      throw new NamingException(409, "ANALYSIS_CHANGED", "Analiza się zmieniła. Odśwież propozycję.");
    }
    FileNameRules.problem(name, file.getOriginalName()).ifPresent(problem -> {
      throw new NamingException(422, "INVALID_NAME", problem);
    });
    var existing = names.findByFile_Id(fileId);
    long currentRevision = existing.map(FileNamingEntity::getRevision).orElse(-1L);
    if (currentRevision != revision) {
      throw new NamingException(409, "REVIEW_CHANGED", "Zapisano już inną decyzję. Odczytaj ją przed zmianą.");
    }
    var entity = existing.orElseGet(() -> new FileNamingEntity(file));
    entity.approve(analysis.id(), sourceSha256, name, Instant.now());
    return dto(file, analysis, names.saveAndFlush(entity));
  }

  private SavedAnalysis analysis(long fileId) {
    return analyses.latest(fileId).orElseThrow(() -> new NamingException(
        409, "ANALYSIS_REQUIRED", "Najpierw wykonaj analizę obrazu."));
  }

  private NamingReviewDto dto(FileRecordEntity file, SavedAnalysis analysis, FileNamingEntity naming) {
    boolean matches = analysis.result().sourceSha256().equals(file.getSha256());
    var proposal = matches ? suggestion(file.getOriginalName(), analysis.result()) : null;
    return new NamingReviewDto(file.getId(), analysis.id(), analysis.result().sourceSha256(),
        file.getOriginalName(), proposal == null ? null : proposal.name(),
        proposal != null && proposal.uncertain(), matches, naming == null ? null : naming.getApprovedName(),
        naming == null ? null : naming.getApprovedAt(),
        naming != null && naming.getSourceSha256().equals(file.getSha256()),
        naming == null ? -1L : naming.getRevision());
  }

  private NameSuggestion suggestion(String originalName, AnalysisResult result) {
    var ocr = result.ocr().status() == AnalysisResult.Status.COMPLETED ? result.ocr().result().text() : null;
    var visual = result.visual().status() == AnalysisResult.Status.COMPLETED
        ? result.visual().result().description() : null;
    return generator.suggest(originalName, ocr, visual).orElse(null);
  }

  private static void validateId(long fileId) {
    if (fileId <= 0) throw new NamingException(400, "INVALID_REQUEST", "Nieprawidłowe ID pliku.");
  }

  private static NamingException missingFile() {
    return new NamingException(404, "FILE_NOT_FOUND", "Plik nie istnieje w katalogu.");
  }
}
