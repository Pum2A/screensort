package pl.pum.screensort.scanning.result;

/** Liczniki jednego skanu, a nie wszystkich plików zapisanych w katalogu. */
public record ScanSummary(
    int newImages, int rescannedImages, int unchangedImages, int rejectedFiles) {
  public ScanSummary {
    if (newImages < 0 || rescannedImages < 0 || unchangedImages < 0 || rejectedFiles < 0) {
      throw new IllegalArgumentException("Liczniki skanu nie mogą być ujemne.");
    }
  }
}
