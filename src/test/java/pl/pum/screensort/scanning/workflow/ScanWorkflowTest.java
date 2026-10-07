package pl.pum.screensort.scanning.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import pl.pum.screensort.catalog.reading.KnownFilesReader;
import pl.pum.screensort.catalog.writing.CatalogUpdater;
import pl.pum.screensort.scanning.folder.AllowedRoot;
import pl.pum.screensort.scanning.folder.InvalidScanFolderException;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;
import pl.pum.screensort.scanning.image.ImageScanner;
import pl.pum.screensort.scanning.jobs.ScanStatus;
import pl.pum.screensort.scanning.result.RejectedFile;
import pl.pum.screensort.scanning.result.RejectionReason;
import pl.pum.screensort.scanning.result.ScanResult;
import pl.pum.screensort.scanning.result.ScanSummary;

class ScanWorkflowTest {
  private final ImageScanner scanner = mock(ImageScanner.class);
  private final CatalogUpdater updater = mock(CatalogUpdater.class);
  private final ScanFolderValidator validator = mock(ScanFolderValidator.class);
  private final KnownFilesReader reader = mock(KnownFilesReader.class);
  private final ScanWorkflow workflow = new ScanWorkflow(scanner, updater, validator, reader);

  @Test
  void reportsStagesAndReceivedCountersBeforeSaving() throws Exception {
    Path folder = Path.of("photos");
    AllowedRoot root = new AllowedRoot(folder);
    ScanSummary counted = new ScanSummary(0, 0, 0, 1);
    ScanResult result =
        new ScanResult(
            root,
            List.of(),
            List.of(
                new RejectedFile(
                    folder.resolve("notes.txt"), "notes.txt", RejectionReason.UNSUPPORTED_EXTENSION)),
            counted);
    when(validator.validate(folder)).thenReturn(root);
    when(reader.read(root)).thenReturn(Map.of());
    when(scanner.scan(eq(root), eq(Map.of()), any()))
        .thenAnswer(
            invocation -> {
              Consumer<ScanSummary> progress = invocation.getArgument(2);
              progress.accept(counted);
              return result;
            });
    List<ScanStatus> stages = new ArrayList<>();
    List<ScanSummary> summaries = new ArrayList<>();
    doAnswer(
            invocation -> {
              assertEquals(ScanStatus.SAVING, stages.getLast());
              assertEquals(counted, summaries.getLast());
              return null;
            })
        .when(updater).update(root, result);

    assertSame(
        result,
        workflow.scanAndSave(
            folder,
            (status, summary) -> {
              stages.add(status);
              summaries.add(summary);
            }));
    assertEquals(
        List.of(ScanStatus.PREPARING, ScanStatus.SCANNING, ScanStatus.SCANNING, ScanStatus.SAVING),
        stages);
    assertEquals(new ScanSummary(0, 0, 0, 0), summaries.getFirst());
    // COMPLETED ustawia manager dopiero po powrocie z całego workflow.
    assertFalse(stages.contains(ScanStatus.COMPLETED));
    verify(updater).update(root, result);
  }

  @Test
  void failedValidationDoesNotReadOrScanOrSave() throws Exception {
    Path folder = Path.of("missing");
    when(validator.validate(folder)).thenThrow(new InvalidScanFolderException("Missing folder"));
    List<ScanStatus> stages = new ArrayList<>();
    assertThrows(
        InvalidScanFolderException.class,
        () -> workflow.scanAndSave(folder, (status, summary) -> stages.add(status)));
    assertEquals(List.of(ScanStatus.PREPARING), stages);
    verifyNoInteractions(reader, scanner, updater);
  }
}
