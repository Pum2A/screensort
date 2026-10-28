package pl.pum.screensort.gallery.reading;

public class ScanAlreadyRunningException extends RuntimeException {
  public ScanAlreadyRunningException() {
    super("Inny skan jest już w toku. Poczekaj na jego zakończenie.");
  }
}
