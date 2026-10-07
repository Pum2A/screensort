package pl.pum.screensort.scanning.jobs;

public class ScanAlreadyRunningException extends RuntimeException {
  public ScanAlreadyRunningException() {
    super("Inny skan jest już w toku. Poczekaj na jego zakończenie.");
  }
}
