package pl.pum.screensort.analysis.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.analysis.AnalysisResult;
import pl.pum.screensort.analysis.AnalysisResult.Failure;
import pl.pum.screensort.analysis.AnalysisResult.OcrStep;
import pl.pum.screensort.analysis.AnalysisResult.Status;
import pl.pum.screensort.analysis.EngineBusyException;

/**
 * The engine the rest of the app uses. When Qwen (llama-server) is configured it looks at the
 * image, and the Python package reads the text on it, because Qwen does no OCR. When Qwen is
 * unreachable and Python is connected, Python does both. A bad image, a timeout or a busy engine
 * is not a reason to switch: Python would fail the same way.
 */
@Primary
@Service
public class FallbackAnalysisEngine implements AnalysisEngine {
  private static final Logger log = LoggerFactory.getLogger(FallbackAnalysisEngine.class);
  static final String NO_OCR = "Odczyt tekstu wymaga pakietu AI z OCR.";
  private final LlamaAnalysisClient llama;
  private final PythonAnalysisClient python;

  public FallbackAnalysisEngine(LlamaAnalysisClient llama, PythonAnalysisClient python) {
    this.llama = llama;
    this.python = python;
  }

  @Override
  public AnalysisResult analyze(byte[] data, String mediaType, boolean ocr, boolean visual, boolean extendedTime) {
    if (!llama.isConfigured()) return python.analyze(data, mediaType, ocr, visual, extendedTime);
    AnalysisResult seen;
    try {
      seen = llama.analyze(data, mediaType, false, visual, extendedTime);
    } catch (EngineBusyException e) {
      throw e;
    } catch (AnalysisException e) {
      if (e.status() != 503 || !python.isConnected()) throw e;
      log.warn("Qwen engine unavailable, falling back to the Python engine");
      return python.analyze(data, mediaType, ocr, visual, extendedTime);
    }
    if (!ocr) return seen;
    if (!python.isConnected()) {
      // A failed step, not a skipped one: the owner asked for text and should see why there is none.
      return new AnalysisResult(seen.sourceSha256(),
          new OcrStep(Status.FAILED, null, new Failure("OCR_FAILED", NO_OCR)), seen.visual());
    }
    AnalysisResult read = python.analyze(data, mediaType, true, false, extendedTime);
    if (!read.sourceSha256().equals(seen.sourceSha256())) {
      throw new AnalysisException(502, "Nie zgadza się hash wyniku analizy.");
    }
    return new AnalysisResult(seen.sourceSha256(), read.ocr(), seen.visual());
  }
}
