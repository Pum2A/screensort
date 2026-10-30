package pl.pum.screensort.analysis.engine;

import pl.pum.screensort.analysis.AnalysisResult;

public interface AnalysisEngine {
  AnalysisResult analyze(
      byte[] data, String mediaType, boolean ocr, boolean visual, boolean extendedTime);
}
