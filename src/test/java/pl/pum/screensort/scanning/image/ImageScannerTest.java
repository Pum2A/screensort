package pl.pum.screensort.scanning.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.pum.screensort.scanning.folder.AllowedRoot;
import pl.pum.screensort.scanning.hashing.FileHasher;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;
import pl.pum.screensort.scanning.result.AcceptedImage;
import pl.pum.screensort.scanning.result.RejectionReason;
import pl.pum.screensort.scanning.result.ScanResult;

class ImageScannerTest {

  @Test
  void findsSupportedImagesInDemoFolder() throws Exception {
    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      ImageScanner scanner = new ImageScanner(new FileHasher(), workers);
      AllowedRoot allowedRoot = new ScanFolderValidator().validate(Path.of("demo-images"));

      ScanResult result = scanner.scan(allowedRoot, Map.of());

      assertEquals(2, result.accepted().size()); // dwa PNG przeszły
      assertEquals(1, result.rejected().size()); // ZIP trafił do odrzuconych
      for (AcceptedImage image : result.accepted()) {
        assertEquals(Files.size(image.path()), image.sizeBytes());
        assertEquals(
            Files.getLastModifiedTime(image.path()).toInstant(), image.lastModifiedAt());
      }
    }
  }

  @Test
  void rejectsFileWithImageExtensionButUnreadableContents(@TempDir Path folder) throws Exception {
    Files.writeString(folder.resolve("not-an-image.png"), "This is text, not an image.");

    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      ImageScanner scanner = new ImageScanner(new FileHasher(), workers);
      AllowedRoot allowedRoot = new ScanFolderValidator().validate(folder);

      ScanResult result = scanner.scan(allowedRoot, Map.of());

      assertEquals(0, result.accepted().size());
      assertEquals(1, result.rejected().size());
      assertEquals(RejectionReason.UNREADABLE_IMAGE, result.rejected().getFirst().reason());
    }
  }
}
