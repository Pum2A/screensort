package pl.pum.screensort.gallery.reading;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Service;
import pl.pum.screensort.gallery.KnownFilesReader;
import pl.pum.screensort.gallery.CatalogUpdater;
import pl.pum.screensort.metrics.ProductMetrics;
import static pl.pum.screensort.metrics.ProductMetrics.Operation.*;

@Service
public class ScanWorkflow {
  private final ImageScanner imageScanner;
  private final CatalogUpdater catalogUpdater;
  private final ScanFolderValidator folderValidator;
  private final KnownFilesReader knownFilesReader;
  private final ProductMetrics metrics;

  public ScanWorkflow(
      ImageScanner imageScanner,
      CatalogUpdater catalogUpdater,
      ScanFolderValidator folderValidator,
      KnownFilesReader knownFilesReader,
      ProductMetrics metrics) {
    this.imageScanner = imageScanner;
    this.catalogUpdater = catalogUpdater;
    this.folderValidator = folderValidator;
    this.knownFilesReader = knownFilesReader;
    this.metrics = metrics;
  }

  /**
   * Validates the folder, reads what the catalog already knows about it, scans incrementally and
   * saves the result. Known files with unchanged size and date reuse their stored SHA-256.
   * No transaction spans the whole scan; only the catalog update writes.
   */
  public ScanResult scanAndSave(Path folder) throws IOException {
    return scanAndSave(folder, ScanProgressListener.NONE);
  }

  public ScanResult scanAndSave(Path folder, ScanProgressListener progress) throws IOException {
    ScanSummary emptySummary = new ScanSummary(0, 0, 0, 0);
    progress.onProgress(ScanStatus.PREPARING, emptySummary);
    AllowedRoot allowedRoot = metrics.measure(FOLDER_VALIDATION, () -> folderValidator.validate(folder));
    Map<String, KnownFile> knowFilesByPath =
        metrics.measure(KNOWN_FILES_READ, () -> knownFilesReader.read(allowedRoot));
    progress.onProgress(ScanStatus.SCANNING, emptySummary);
    ScanResult result = metrics.measure(SCAN, () ->
        imageScanner.scan(
            allowedRoot,
            knowFilesByPath,
            summary -> progress.onProgress(ScanStatus.SCANNING, summary)));
    metrics.scanned(result.summary());
    progress.onProgress(ScanStatus.SAVING, result.summary());
    metrics.measure(CATALOG_SAVE, () -> {
      catalogUpdater.update(result.allowedRoot(), result);
      return null;
    });
    return result;
  }
}
