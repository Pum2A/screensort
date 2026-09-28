package pl.pum.screensort.scanning;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.stereotype.Service;
import pl.pum.screensort.catalog.CatalogUpdater;
import pl.pum.screensort.scanning.image.ImageScanner;
import pl.pum.screensort.scanning.image.ScanResult;

@Service
public class ScanWorkflow {
  private final ImageScanner imageScanner;
  private final CatalogUpdater catalogUpdater;

  public ScanWorkflow(ImageScanner imageScanner, CatalogUpdater catalogUpdater) {
    this.imageScanner = imageScanner;
    this.catalogUpdater = catalogUpdater;
  }

  public ScanResult scanAndSave(Path folder) throws IOException {
    long start = System.nanoTime();
    ScanResult result = imageScanner.scan(folder);
    long afterScan = System.nanoTime();

    catalogUpdater.update(result.allowedRoot(), result);
    long afterSave = System.nanoTime();

    double scanMs = (afterScan - start) / 1_000_000.0;
    double databaseMs = (afterSave - afterScan) / 1_000_000.0;

    System.out.println("Skanowanie: " + scanMs + " ms");
    System.out.println("Baza danych: " + databaseMs + " ms");

    return result;
  }
}
