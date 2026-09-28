package pl.pum.screensort.scanning.image;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import pl.pum.screensort.scanning.hashing.FileHasher;
import pl.pum.screensort.scanning.safety.ScanFolderValidator;

class ImageScannerTimingTest {

  @Test
  @EnabledIfSystemProperty(named = "screensort.scan.benchmark.folder", matches = ".+")
  void measureTwoReadOnlyScansOfSelectedFolder() throws Exception {
    Path folder = Path.of(System.getProperty("screensort.scan.benchmark.folder"));
    ImageScanner scanner = new ImageScanner(new ScanFolderValidator(), new FileHasher());

    for (int run = 1; run <= 2; run++) {
      long startedAt = System.nanoTime();
      ScanResult result = scanner.scan(folder);
      double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0;

      assertNotNull(result.allowedRoot());
      System.out.printf(Locale.ROOT,
          "Read-only scan %d: %.3f ms; accepted=%d; rejected=%d%n",
          run, elapsedMs, result.accepted().size(), result.rejected().size());
    }
  }
}
