package pl.pum.screensort.diagnostics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;
import pl.pum.screensort.analysis.AnalysisResult;
import pl.pum.screensort.gallery.reading.ScanSummary;

/** Fixed labels only: never paths, filenames, OCR, hashes, tokens or job IDs. */
@Component
public class ProductMetrics {
  public enum Operation {
    FOLDER_VALIDATION, KNOWN_FILES_READ, SCAN, CATALOG_SAVE,
    ANALYSIS_SOURCE_READ, ANALYSIS_REQUEST, ANALYSIS_SAVE
  }
  public enum CacheLayer { MEMORY, DISK, REDIS }
  public enum CacheOutcome { HIT, MISS, ERROR, BYPASS }

  @FunctionalInterface
  public interface CheckedAction<T, E extends Exception> { T run() throws E; }

  private final MeterRegistry registry;

  public ProductMetrics(MeterRegistry registry) { this.registry = registry; }

  public <T, E extends Exception> T measure(Operation operation, CheckedAction<T, E> action) throws E {
    var sample = Timer.start(registry);
    boolean success = false;
    try {
      T value = action.run();
      success = true;
      return value;
    } finally {
      sample.stop(registry.timer("screensort.operation", "operation", label(operation),
          "outcome", success ? "success" : "failure"));
    }
  }

  public void scanned(ScanSummary summary) {
    countFiles("new", summary.newImages());
    countFiles("rescanned", summary.rescannedImages());
    countFiles("unchanged", summary.unchangedImages());
    countFiles("rejected", summary.rejectedFiles());
  }

  public void analyzed(AnalysisResult result) {
    analysisStep("ocr", result.ocr().status(),
        result.ocr().result() == null ? null : result.ocr().result().durationMs());
    analysisStep("visual", result.visual().status(),
        result.visual().result() == null ? null : result.visual().result().durationMs());
  }

  public void cache(CacheLayer layer, CacheOutcome outcome) {
    registry.counter("screensort.preview.cache", "layer", label(layer), "outcome", label(outcome))
        .increment();
  }

  private void countFiles(String outcome, int count) {
    registry.counter("screensort.scan.files", "outcome", outcome).increment(count);
  }

  private void analysisStep(String step, AnalysisResult.Status status, Double milliseconds) {
    registry.counter("screensort.analysis.steps", "step", step, "outcome", label(status)).increment();
    if (milliseconds != null) {
      registry.timer("screensort.analysis.inference", "step", step)
          .record(Duration.ofNanos(Math.round(milliseconds * 1_000_000)));
    }
  }

  private static String label(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
}
