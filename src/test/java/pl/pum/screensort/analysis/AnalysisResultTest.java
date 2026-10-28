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
}
