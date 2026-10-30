package pl.pum.screensort.analysis.engine;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.analysis.AnalysisResult;
import pl.pum.screensort.analysis.AnalysisResult.Status;
import pl.pum.screensort.analysis.EngineBusyException;

class FallbackAnalysisEngineTest {
  private static final String HASH = "a".repeat(64);
  private final LlamaAnalysisClient llama = mock(LlamaAnalysisClient.class);
  private final PythonAnalysisClient python = mock(PythonAnalysisClient.class);
  private final FallbackAnalysisEngine engine = new FallbackAnalysisEngine(llama, python);
  private final byte[] data = {1};

  @Test void pythonAnswersAloneWhenQwenIsNotConfigured() {
    var result = pythonResult(HASH);
    when(python.analyze(data, "image/png", true, true, false)).thenReturn(result);
    assertThat(engine.analyze(data, "image/png", true, true, false)).isSameAs(result);
    verify(llama, never()).analyze(any(), any(), anyBoolean(), anyBoolean(), anyBoolean());
  }

  @Test void qwenSeesTheImageAndPythonReadsTheText() {
    when(llama.isConfigured()).thenReturn(true);
    when(python.isConnected()).thenReturn(true);
    var seen = qwenResult();
    when(llama.analyze(data, "image/png", false, true, true)).thenReturn(seen);
    when(python.analyze(data, "image/png", true, false, true)).thenReturn(pythonResult(HASH));

    var result = engine.analyze(data, "image/png", true, true, true);

    assertThat(result.visual()).isEqualTo(seen.visual());
    assertThat(result.ocr().result().text()).isEqualTo("Faktura");
  }

  @Test void withoutPythonTheTextStepFailsVisiblyAndOnlyTextSkipsQwen() {
    when(llama.isConfigured()).thenReturn(true);
    when(llama.analyze(data, "image/png", false, true, false)).thenReturn(qwenResult());
    var result = engine.analyze(data, "image/png", true, true, false);
    assertThat(result.ocr().status()).isEqualTo(Status.FAILED);
    assertThat(result.ocr().error().message()).isEqualTo(FallbackAnalysisEngine.NO_OCR);
    assertThat(result.visual().status()).isEqualTo(Status.COMPLETED);
  }

  @Test void qwenAloneAnswersWhenNoTextIsAsked() {
    when(llama.isConfigured()).thenReturn(true);
    var seen = qwenResult();
    when(llama.analyze(data, "image/png", false, true, false)).thenReturn(seen);
    assertThat(engine.analyze(data, "image/png", false, true, false)).isSameAs(seen);
    verify(python, never()).analyze(any(), any(), anyBoolean(), anyBoolean(), anyBoolean());
  }

  @Test void anUnreachableQwenFallsBackToAConnectedPython() {
    when(llama.isConfigured()).thenReturn(true);
    when(llama.analyze(data, "image/png", false, true, true)).thenThrow(new AnalysisException(503, "down"));
    when(python.isConnected()).thenReturn(true);
    var result = pythonResult(HASH);
    when(python.analyze(data, "image/png", true, true, true)).thenReturn(result);
    assertThat(engine.analyze(data, "image/png", true, true, true)).isSameAs(result);
  }

  @Test void badImagesTimeoutsBadAnswersAndBusyEnginesDoNotSwitchEngines() {
    when(llama.isConfigured()).thenReturn(true);
    when(python.isConnected()).thenReturn(true);
    for (var error : new AnalysisException[]{new AnalysisException(422, "bad"), new AnalysisException(504, "slow"),
        new AnalysisException(502, "garbage"), new EngineBusyException(Duration.ofSeconds(1))}) {
      doThrow(error).when(llama).analyze(data, "image/png", false, true, false);
      assertThatThrownBy(() -> engine.analyze(data, "image/png", false, true, false)).isSameAs(error);
    }
    verify(python, never()).analyze(any(), any(), anyBoolean(), anyBoolean(), anyBoolean());
  }

  @Test void withoutPythonTheQwenErrorIsKept() {
    when(llama.isConfigured()).thenReturn(true);
    var down = new AnalysisException(503, "down");
    when(llama.analyze(data, "image/png", false, true, false)).thenThrow(down);
    assertThatThrownBy(() -> engine.analyze(data, "image/png", false, true, false)).isSameAs(down);
  }

  @Test void textForAnotherImageIsRejected() {
    when(llama.isConfigured()).thenReturn(true);
    when(python.isConnected()).thenReturn(true);
    when(llama.analyze(data, "image/png", false, true, false)).thenReturn(qwenResult());
    when(python.analyze(data, "image/png", true, false, false)).thenReturn(pythonResult("b".repeat(64)));
    assertThatThrownBy(() -> engine.analyze(data, "image/png", true, true, false))
        .isInstanceOfSatisfying(AnalysisException.class, error -> assertThat(error.status()).isEqualTo(502));
  }

  private static AnalysisResult qwenResult() {
    var subject = new AnalysisResult.Subject("Discord", "czat", "chat", "Czaty");
    return new AnalysisResult(HASH, new AnalysisResult.OcrStep(Status.SKIPPED, null, null),
        new AnalysisResult.VisualStep(Status.COMPLETED,
            new AnalysisResult.Visual("Discord: chat", "qwen", "1", "screenshot", "gpu", 1, subject), null));
  }

  private static AnalysisResult pythonResult(String hash) {
    return new AnalysisResult(hash,
        new AnalysisResult.OcrStep(Status.COMPLETED, new AnalysisResult.Ocr("Faktura", "rapidocr", "1", 1), null),
        new AnalysisResult.VisualStep(Status.SKIPPED, null, null));
  }
}
