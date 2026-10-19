package pl.pum.screensort.organization.review;

public class ReviewException extends RuntimeException {
  private final int status;
  private final String code;

  public ReviewException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }
  public int status() { return status; }
  public String code() { return code; }
}
