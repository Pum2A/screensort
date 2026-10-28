package pl.pum.screensort.analysis;

import java.util.Optional;
import org.springframework.stereotype.Service;
import pl.pum.screensort.analysis.engine.PythonAnalysisClient;
import pl.pum.screensort.metrics.ProductMetrics;
import static pl.pum.screensort.metrics.ProductMetrics.Operation.*;

/** One inference at a time across single-image and folder jobs. No open DB transaction here. */
@Service
public class AnalysisProcessor {
  private final AnalysisSourceReader sources;
  private final PythonAnalysisClient python;
  private final AnalysisStore store;
  private final ProductMetrics metrics;

  public AnalysisProcessor(AnalysisSourceReader sources, PythonAnalysisClient python, AnalysisStore store,
      ProductMetrics metrics) {
    this.sources = sources; this.python = python; this.store = store;
    this.metrics = metrics;
  }

  /** The saved result when this content was already analyzed with these options; no model call. */
  public Optional<SavedAnalysis> reusable(long fileId, boolean ocr, boolean visual, String sha256) {
    return store.reusable(fileId, ocr, visual, sha256);
  }

  public SavedAnalysis process(long fileId, boolean ocr, boolean visual, String expectedHash) {
    return process(fileId, ocr, visual, expectedHash, false);
  }

  /** {@code extendedTime}: the second, last try for a file whose analysis timed out once. */
  public SavedAnalysis process(long fileId, boolean ocr, boolean visual, String expectedHash, boolean extendedTime) {
    return process(fileId, ocr, visual, expectedHash, () -> {}, extendedTime);
  }

  public SavedAnalysis process(long fileId, boolean ocr, boolean visual,
      String expectedHash, Runnable beforeSave) {
    return process(fileId, ocr, visual, expectedHash, beforeSave, false);
  }

  private synchronized SavedAnalysis process(long fileId, boolean ocr, boolean visual,
      String expectedHash, Runnable beforeSave, boolean extendedTime) {
    var input = metrics.measure(ANALYSIS_SOURCE_READ, () -> sources.read(fileId));
    if (expectedHash != null && !expectedHash.equals(input.sha256())) {
      throw new AnalysisException(409, "Plik zmienił się od skanu. Zeskanuj folder ponownie.");
    }
    var result = metrics.measure(ANALYSIS_REQUEST,
        () -> python.analyze(input.bytes(), input.mediaType(), ocr, visual, extendedTime));
    if (!input.sha256().equals(result.sourceSha256())) {
      throw new AnalysisException(502, "Nie zgadza się hash wyniku analizy.");
    }
    if ((ocr == (result.ocr().status() == AnalysisResult.Status.SKIPPED))
        || (visual == (result.visual().status() == AnalysisResult.Status.SKIPPED))) {
      throw new AnalysisException(502, "Serwis zwrócił wynik niezgodny z zamówioną analizą.");
    }
    metrics.analyzed(result);
    beforeSave.run();
    return metrics.measure(ANALYSIS_SAVE, () -> store.save(fileId, ocr, visual, result));
  }
}
