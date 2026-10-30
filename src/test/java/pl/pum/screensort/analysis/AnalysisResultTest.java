package pl.pum.screensort.analysis;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AnalysisResultTest {
  static AnalysisResult result(String hash) {
    return new AnalysisResult(hash,
        new AnalysisResult.OcrStep(AnalysisResult.Status.COMPLETED,
            new AnalysisResult.Ocr("Test", "test-only", "0", 1), null),
        new AnalysisResult.VisualStep(AnalysisResult.Status.SKIPPED, null, null));
  }

  @Test void serializesThePythonContractWithoutLosingTypes() {
    var json = JsonMapper.builder().build();
    var result = result("a".repeat(64));
    String encoded = json.writeValueAsString(result);
    assertThat(encoded).contains("source_sha256", "engine_version", "duration_ms");
    assertThat(json.readValue(encoded, AnalysisResult.class)).isEqualTo(result);
  }

  @Test void rejectsContradictoryStatusesAndInvalidHashes() {
    assertThatThrownBy(() -> new AnalysisResult.OcrStep(AnalysisResult.Status.COMPLETED, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AnalysisResult.VisualStep(AnalysisResult.Status.FAILED, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> result("not-a-hash")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test void aScreenshotResultCarriesTheModelSubjectAndOldResultsStillRead() {
    var json = JsonMapper.builder().build();
    var subject = new AnalysisResult.Subject("Discord", "planowanie wyjazdu", "trip planning", "Czaty");
    var visual = new AnalysisResult.Visual("Discord: trip planning", "qwen3.5-2b", "gguf-q4_k_m", "screenshot",
        "gpu", 800, subject);
    var result = new AnalysisResult("a".repeat(64), new AnalysisResult.OcrStep(AnalysisResult.Status.SKIPPED, null, null),
        new AnalysisResult.VisualStep(AnalysisResult.Status.COMPLETED, visual, null));
    String encoded = json.writeValueAsString(result);
    assertThat(encoded).contains("\"topic_pl\":\"planowanie wyjazdu\"", "\"topic_en\"");
    assertThat(json.readValue(encoded, AnalysisResult.class)).isEqualTo(result);

    // A result stored by the Python engine before this field existed.
    String old = """
        {"source_sha256":"%s","ocr":{"status":"SKIPPED"},"visual":{"status":"COMPLETED","result":
        {"description":"A mug","model":"florence-2","model_revision":"r1","task":"caption","device":"cpu",
        "duration_ms":5}}}""".formatted("b".repeat(64));
    assertThat(json.readValue(old, AnalysisResult.class).visual().result().subject()).isNull();
  }

  @Test void aSubjectNeedsAKnownCategoryAndOnlyComesWithTheScreenshotTask() {
    assertThatThrownBy(() -> new AnalysisResult.Subject("App", "temat", "topic", "Kosmos"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AnalysisResult.Subject(" ", "temat", "topic", "Gry"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AnalysisResult.Visual("x", "m", "r", "screenshot", "gpu", 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AnalysisResult.Visual("x", "m", "r", "caption", "cpu", 1,
        new AnalysisResult.Subject("App", "temat", "topic", "Gry"))).isInstanceOf(IllegalArgumentException.class);
  }
}
