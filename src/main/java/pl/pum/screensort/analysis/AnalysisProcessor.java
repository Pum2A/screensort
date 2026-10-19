package pl.pum.screensort.analysis;

import org.springframework.stereotype.Service;
import pl.pum.screensort.analysis.client.PythonAnalysisClient;
import pl.pum.screensort.analysis.database.AnalysisStore;
import pl.pum.screensort.analysis.result.AnalysisResult;
import pl.pum.screensort.analysis.result.SavedAnalysis;
import pl.pum.screensort.analysis.source.AnalysisSourceReader;

/** One inference at a time across single-image and folder jobs. No open DB transaction here. */
@Service
public class AnalysisProcessor {
  private final AnalysisSourceReader sources;
  private final PythonAnalysisClient python;
  private final AnalysisStore store;

  public AnalysisProcessor(AnalysisSourceReader sources, PythonAnalysisClient python, AnalysisStore store) {
    this.sources = sources; this.python = python; this.store = store;
  }

  public SavedAnalysis process(long fileId, boolean ocr, boolean visual, String expectedHash) {
    return process(fileId, ocr, visual, expectedHash, () -> {});
  }

  public synchronized SavedAnalysis process(long fileId, boolean ocr, boolean visual,
      String expectedHash, Runnable beforeSave) {
    var input = sources.read(fileId);
    if (expectedHash != null && !expectedHash.equals(input.sha256())) {
      throw new AnalysisException(409, "Plik zmienił się od skanu. Zeskanuj folder ponownie.");
    }
    var result = python.analyze(input.bytes(), input.mediaType(), ocr, visual);
    if (!input.sha256().equals(result.sourceSha256())) {
      throw new AnalysisException(502, "Nie zgadza się hash wyniku analizy.");
    }
    if ((ocr == (result.ocr().status() == AnalysisResult.Status.SKIPPED))
        || (visual == (result.visual().status() == AnalysisResult.Status.SKIPPED))) {
      throw new AnalysisException(502, "Serwis zwrócił wynik niezgodny z zamówioną analizą.");
    }
    beforeSave.run();
    return store.save(fileId, ocr, visual, result);
  }
}
