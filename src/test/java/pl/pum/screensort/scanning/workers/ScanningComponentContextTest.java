package pl.pum.screensort.scanning.workers;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import pl.pum.screensort.scanning.hashing.FileHasher;
import pl.pum.screensort.scanning.image.ImageScanner;

class ScanningComponentContextTest {

  @Test
  void createsScannerWithAllRequiredDependencies() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.scan(
          "pl.pum.screensort.scanning.hashing",
          "pl.pum.screensort.scanning.image",
          "pl.pum.screensort.scanning.workers");
      context.refresh();

      assertNotNull(context.getBean(FileHasher.class));
      assertNotNull(context.getBean(ImageScanner.class));
    }
  }
}
