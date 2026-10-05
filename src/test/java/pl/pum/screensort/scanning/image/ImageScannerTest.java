package pl.pum.screensort.scanning.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import pl.pum.screensort.scanning.hashing.FileHasher;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;
import pl.pum.screensort.scanning.result.ScanResult;

class ImageScannerTest {

  @Test
  void findsSupportedImagesInDemoFolder() throws Exception {
    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      ImageScanner scanner =
          new ImageScanner(new ScanFolderValidator(), new FileHasher(), workers);

      ScanResult result = scanner.scan(Path.of("demo-images"));

      assertEquals(2, result.accepted().size()); // dwa PNG przeszły
      assertEquals(1, result.rejected().size()); // ZIP trafił do odrzuconych
    }
  }
}
