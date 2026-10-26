package pl.pum.screensort.analysis;

import java.time.Duration;

/** The engine works, but is still finishing another image. Waiting helps; pausing the batch does not. */
public class EngineBusyException extends AnalysisException {
  private final Duration retryAfter;

  public EngineBusyException(Duration retryAfter) {
    super(503, "Silnik analizy kończy poprzedni obraz. Spróbuj za chwilę.");
    this.retryAfter = retryAfter;
  }

  public Duration retryAfter() { return retryAfter; }
}
