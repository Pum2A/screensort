package pl.pum.screensort.analysis;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pum.screensort.gallery.FileRecordRepository;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AnalysisStore {
  private final FileAnalysisRepository analyses;
  private final FileRecordRepository files;
  private final JsonMapper json;

  public AnalysisStore(FileAnalysisRepository analyses, FileRecordRepository files, JsonMapper json) {
    this.analyses = analyses; this.files = files; this.json = json;
  }

  @Transactional
  public SavedAnalysis save(long fileId, boolean ocr, boolean visual, AnalysisResult result) {
    var file = files.findById(fileId).orElseThrow(() -> new AnalysisException(404, "Plik nie istnieje w katalogu."));
    var entity = analyses.findByFile_IdAndOcrEnabledAndVisualEnabled(fileId, ocr, visual)
        .orElseGet(() -> new FileAnalysisEntity(file, ocr, visual));
    entity.update(result.sourceSha256(), json.writeValueAsString(result), Instant.now());
    return dto(analyses.saveAndFlush(entity));
  }

  /** A finished result for exactly this content and these options, so a folder batch can skip the model. */
  @Transactional(readOnly = true)
  public Optional<SavedAnalysis> reusable(long fileId, boolean ocr, boolean visual, String sha256) {
    return analyses.findByFile_IdAndOcrEnabledAndVisualEnabled(fileId, ocr, visual)
        .filter(entity -> entity.getSourceSha256().equals(sha256))
        .map(this::dto)
        .filter(saved -> (!ocr || saved.result().ocr().status() == AnalysisResult.Status.COMPLETED)
            && (!visual || saved.result().visual().status() == AnalysisResult.Status.COMPLETED));
  }

  @Transactional(readOnly = true)
  public Optional<SavedAnalysis> latest(long fileId) {
    return analyses.findFirstByFile_IdOrderByAnalyzedAtDesc(fileId).map(this::dto);
  }

  @Transactional(readOnly = true)
  public Optional<SavedAnalysis> find(long analysisId) {
    return analyses.findById(analysisId).map(this::dto);
  }

  private SavedAnalysis dto(FileAnalysisEntity entity) {
    var file = entity.getFile();
    return new SavedAnalysis(entity.getId(), file.getId(), entity.getAnalyzedAt(),
        entity.getSourceSha256().equals(file.getSha256()),
        json.readValue(entity.getResultJson(), AnalysisResult.class));
  }
}
