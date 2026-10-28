package pl.pum.screensort.gallery.preview;

public class ImagePreviewException extends RuntimeException {
  public enum Reason {
    NOT_FOUND, UNSAFE_PATH, TOO_LARGE, INVALID_IMAGE, CHANGED, BUSY
  }

  private final Reason reason;

  public ImagePreviewException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
