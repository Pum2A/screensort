package pl.pum.screensort.analysis.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Set;

/** The versioned Python response, never a JPA entity or a source path. */
public record AnalysisResult(
    @JsonProperty("source_sha256") String sourceSha256, OcrStep ocr, VisualStep visual) {
  public enum Status { COMPLETED, FAILED, SKIPPED }

  public AnalysisResult {
    if (sourceSha256 == null || !sourceSha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Invalid analysis content hash");
    }
    Objects.requireNonNull(ocr);
    Objects.requireNonNull(visual);
  }

  public record Failure(String code, String message) {
    public Failure {
      if (!Set.of("OCR_FAILED", "VISUAL_FAILED").contains(code)) {
        throw new IllegalArgumentException("Unknown analysis error");
      }
      if (message == null || message.length() > 500) {
        throw new IllegalArgumentException("Invalid analysis message");
      }
    }
  }

  public record OcrStep(Status status, Ocr result, Failure error) {
    public OcrStep { validateStep(status, result, error); }
  }

  public record VisualStep(Status status, Visual result, Failure error) {
    public VisualStep { validateStep(status, result, error); }
  }

  public record Ocr(String text, String engine,
      @JsonProperty("engine_version") String engineVersion,
      @JsonProperty("duration_ms") double durationMs) {
    public Ocr {
      if (text == null || text.length() > 1_000_000) throw new IllegalArgumentException("Invalid OCR text");
      requireText(engine, 100);
      requireText(engineVersion, 100);
      requireDuration(durationMs);
    }
  }

  public record Visual(String description, String model,
      @JsonProperty("model_revision") String modelRevision, String task, String device,
      @JsonProperty("duration_ms") double durationMs) {
    public Visual {
      requireText(description, 20_000);
      requireText(model, 200);
      requireText(modelRevision, 100);
      if (!Set.of("caption", "detailed_caption").contains(task)
          || !Set.of("cpu", "cuda").contains(device)) throw new IllegalArgumentException("Unknown visual mode");
      requireDuration(durationMs);
    }
  }

  private static void validateStep(Status status, Object result, Failure error) {
    Objects.requireNonNull(status);
    if ((status == Status.COMPLETED) != (result != null)
        || (status == Status.FAILED) != (error != null)) {
      throw new IllegalArgumentException("Contradictory analysis status");
    }
  }

  private static void requireText(String value, int max) {
    if (value == null || value.isBlank() || value.length() > max) {
      throw new IllegalArgumentException("Invalid analysis metadata");
    }
  }

  private static void requireDuration(double value) {
    if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid duration");
  }
}
