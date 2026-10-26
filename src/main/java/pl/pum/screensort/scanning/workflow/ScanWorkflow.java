package pl.pum.screensort.scanning.workflow;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Service;
import pl.pum.screensort.catalog.reading.KnownFilesReader;
import pl.pum.screensort.catalog.writing.CatalogUpdater;
import pl.pum.screensort.diagnostics.ProductMetrics;
import static pl.pum.screensort.diagnostics.ProductMetrics.Operation.*;
import pl.pum.screensort.scanning.folder.AllowedRoot;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;
import pl.pum.screensort.scanning.image.ImageScanner;
import pl.pum.screensort.scanning.jobs.ScanStatus;
import pl.pum.screensort.scanning.knownfiles.KnownFile;
import pl.pum.screensort.scanning.result.ScanResult;
import pl.pum.screensort.scanning.result.ScanSummary;

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
