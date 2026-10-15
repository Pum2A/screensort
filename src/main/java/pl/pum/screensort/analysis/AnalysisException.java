package pl.pum.screensort.analysis;

public class AnalysisException extends RuntimeException {
  private final int status;
  public AnalysisException(int status, String message) { super(message); this.status = status; }
  public int status() { return status; }
}
