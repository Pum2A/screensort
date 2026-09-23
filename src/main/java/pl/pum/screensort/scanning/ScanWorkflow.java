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
    ScanResult result = imageScanner.scan(folder);
    catalogUpdater.update(result.allowedRoot(), result);
    return result;
  }
}
