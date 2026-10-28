package pl.pum.screensort.gallery.reading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScanSummaryTest {

  @ParameterizedTest
  @CsvSource({"-1, 0, 0, 0", "0, -1, 0, 0", "0, 0, -1, 0", "0, 0, 0, -1"})
  void rejectsNegativeCounters(int newImages, int rescanned, int unchanged, int rejected) {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ScanSummary(newImages, rescanned, unchanged, rejected));
  }

  @Test
  void allowsEmptyScan(@TempDir Path folder) {
    ScanSummary summary = new ScanSummary(0, 0, 0, 0);
    ScanResult result = new ScanResult(new AllowedRoot(folder), List.of(), List.of(), summary);

    assertEquals(summary, result.summary());
  }

  @Test
  void rejectsSummaryThatDoesNotMatchAcceptedList(@TempDir Path folder) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ScanResult(
                new AllowedRoot(folder), List.of(), List.of(), new ScanSummary(1, 0, 0, 0)));
  }

  @Test
  void rejectsSummaryThatDoesNotMatchRejectedList(@TempDir Path folder) {
    RejectedFile file =
        new RejectedFile(
            folder.resolve("archive.zip"), "archive.zip", RejectionReason.UNSUPPORTED_EXTENSION);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ScanResult(
                new AllowedRoot(folder), List.of(), List.of(file), new ScanSummary(0, 0, 0, 0)));
  }
}
