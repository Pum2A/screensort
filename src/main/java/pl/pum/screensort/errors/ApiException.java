package pl.pum.screensort.errors;

/** A request the controller rejects itself. The message is shown to the user as written. */
public class ApiException extends RuntimeException {
  private final int status;
  private final String code;

  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() { return status; }
  public String code() { return code; }
}
