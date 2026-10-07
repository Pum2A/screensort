package pl.pum.screensort.scanning.jobs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import pl.pum.screensort.scanning.folder.AllowedRoot;
import pl.pum.screensort.scanning.folder.InvalidScanFolderException;
import pl.pum.screensort.scanning.result.RejectedFile;
import pl.pum.screensort.scanning.result.RejectionReason;
import pl.pum.screensort.scanning.result.ScanResult;
import pl.pum.screensort.scanning.result.ScanSummary;
import pl.pum.screensort.scanning.workflow.ScanProgressListener;
import pl.pum.screensort.scanning.workflow.ScanWorkflow;

class ScanJobManagerTest {
  private final ScanWorkflow workflow = mock(ScanWorkflow.class);
  // W testach decydujemy dokładnie, kiedy wykonać zleconą pracę; bez sleep i wyścigów.
  private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
  private final ScanJobManager jobs = new ScanJobManager(workflow, tasks::add);
  private final Path folder = Path.of("photos");

  @Test
  void returnsIdBeforeWorkflowRunsAndPublishesNewSnapshots() throws Exception {
    ScanJob initial = jobs.start(folder);
    assertEquals(ScanStatus.PREPARING, initial.status());
    assertEquals(new ScanSummary(0, 0, 0, 0), initial.summary());
    assertTrue(jobs.findRejections(initial.id()).isEmpty());
    verifyNoInteractions(workflow);
    ScanSummary counted = new ScanSummary(0, 0, 0, 1);
    ScanResult result = rejectedResult(folder);
    when(workflow.scanAndSave(eq(folder), any(ScanProgressListener.class)))
        .thenAnswer(
            invocation -> {
              ScanProgressListener progress = invocation.getArgument(1);
              progress.onProgress(ScanStatus.SCANNING, counted);
              assertEquals(counted, jobs.find(initial.id()).orElseThrow().summary());
              progress.onProgress(ScanStatus.SAVING, counted);
              assertEquals(ScanStatus.SAVING, jobs.find(initial.id()).orElseThrow().status());
              return result;
            });

    tasks.remove().run();

    ScanJob finished = jobs.find(initial.id()).orElseThrow();
    assertEquals(ScanStatus.COMPLETED, finished.status());
    assertEquals(counted, finished.summary());
    assertNull(finished.errorMessage());
    assertEquals(result.rejected(), jobs.findRejections(initial.id()).orElseThrow());
    assertEquals(ScanStatus.PREPARING, initial.status()); // stary record się nie zmienił
    assertEquals(new ScanSummary(0, 0, 0, 0), initial.summary());
  }

  @Test
  void blocksAnotherFolderAndLegacyScanUntilTheActiveJobFinishes() throws Exception {
    ScanJob first = jobs.start(folder);
    assertThrows(ScanAlreadyRunningException.class, () -> jobs.start(Path.of("other-photos")));
    assertThrows(ScanAlreadyRunningException.class, () -> jobs.scanAndSave(folder));
    when(workflow.scanAndSave(eq(folder), any(ScanProgressListener.class)))
        .thenReturn(emptyResult(folder));
    tasks.remove().run();

    ScanJob second = jobs.start(folder);
    assertNotEquals(first.id(), second.id());
    assertEquals(ScanStatus.COMPLETED, jobs.find(first.id()).orElseThrow().status());
  }

  @Test
  void recordsFailurePreservesCountersAndReleasesTheActiveSlot() throws Exception {
    ScanJob initial = jobs.start(folder);
    ScanSummary counted = new ScanSummary(0, 0, 0, 1);
    when(workflow.scanAndSave(eq(folder), any(ScanProgressListener.class)))
        .thenAnswer(
            invocation -> {
              ScanProgressListener progress = invocation.getArgument(1);
              progress.onProgress(ScanStatus.SAVING, counted);
              throw new IllegalStateException("SQL/internal connection details");
            });
    tasks.remove().run();

    ScanJob failed = jobs.find(initial.id()).orElseThrow();
    assertEquals(ScanStatus.FAILED, failed.status());
    assertEquals(counted, failed.summary());
    assertFalse(failed.errorMessage().contains("SQL"));
    assertDoesNotThrow(() -> jobs.start(folder));
  }

  @Test
  void turnsInvalidFoldersIntoFailedBackgroundJobs() throws Exception {
    when(workflow.scanAndSave(eq(folder), any(ScanProgressListener.class)))
        .thenThrow(new InvalidScanFolderException("Wybrany folder nie istnieje."));
    ScanJob initial = jobs.start(folder);
    tasks.remove().run();
    ScanJob failed = jobs.find(initial.id()).orElseThrow();
    assertEquals(ScanStatus.FAILED, failed.status());
    assertEquals("Wybrany folder nie istnieje.", failed.errorMessage());
  }

  @Test
  void synchronousCompatibilityPathPropagatesFailureAndReleasesTheSlot() throws Exception {
    when(workflow.scanAndSave(eq(folder), any(ScanProgressListener.class)))
        .thenThrow(new IOException("failed read"));
    assertThrows(IOException.class, () -> jobs.scanAndSave(folder));
    assertDoesNotThrow(() -> jobs.start(folder));
  }

  @Test
  void executorRejectionDoesNotLeaveScanningPermanentlyBlocked() {
    AtomicInteger attempts = new AtomicInteger();
    TaskExecutor runner =
        task -> {
          if (attempts.getAndIncrement() == 0) {
            throw new TaskRejectedException("Executor stopped");
          }
          tasks.add(task);
        };
    ScanJobManager manager = new ScanJobManager(workflow, runner);
    assertThrows(TaskRejectedException.class, () -> manager.start(folder));
    assertDoesNotThrow(() -> manager.start(folder));
  }

  @Test
  void retainsOnlyTwentyRecentSnapshotsAndReturnsEmptyForUnknownIds() throws Exception {
    when(workflow.scanAndSave(any(Path.class), any(ScanProgressListener.class)))
        .thenAnswer(invocation -> emptyResult(invocation.getArgument(0)));
    ScanJob oldest = jobs.start(folder);
    tasks.remove().run();
    for (int index = 0; index < 19; index++) {
      jobs.start(folder);
      tasks.remove().run();
    }
    assertTrue(jobs.find(oldest.id()).isPresent());
    ScanJob newest = jobs.start(folder);
    tasks.remove().run();
    assertTrue(jobs.find(oldest.id()).isEmpty());
    assertTrue(jobs.findRejections(oldest.id()).isEmpty());
    assertEquals(ScanStatus.COMPLETED, jobs.find(newest.id()).orElseThrow().status());
    assertTrue(jobs.find(UUID.randomUUID()).isEmpty());
  }

  @Test
  void keepsAtMostOneHundredRejectionsWithoutChangingTheTotal() throws Exception {
    var rejected = java.util.stream.IntStream.range(0, 125)
        .mapToObj(index -> new RejectedFile(folder.resolve(index + ".txt"),
            index + ".txt", RejectionReason.UNSUPPORTED_EXTENSION))
        .toList();
    when(workflow.scanAndSave(eq(folder), any(ScanProgressListener.class)))
        .thenReturn(new ScanResult(new AllowedRoot(folder), List.of(), rejected,
            new ScanSummary(0, 0, 0, 125)));
    ScanJob initial = jobs.start(folder);
    tasks.remove().run();
    var sample = jobs.findRejections(initial.id()).orElseThrow();
    assertEquals(100, sample.size());
    assertEquals(125, jobs.find(initial.id()).orElseThrow().summary().rejectedFiles());
    assertThrows(UnsupportedOperationException.class, () -> sample.clear());
    assertEquals(125, rejected.size());
  }

  @Test
  void simultaneousStartsReserveOnlyOneJob() throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    try (var callers = Executors.newFixedThreadPool(2)) {
      var attempt =
          (java.util.concurrent.Callable<Boolean>)
              () -> {
                ready.countDown();
                assertTrue(go.await(5, TimeUnit.SECONDS));
                try {
                  jobs.start(folder);
                  return true;
                } catch (ScanAlreadyRunningException exception) {
                  return false;
                }
              };
      var first = callers.submit(attempt);
      var second = callers.submit(attempt);
      try {
        assertTrue(ready.await(5, TimeUnit.SECONDS));
      } finally {
        go.countDown();
      }
      assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
      assertEquals(1, tasks.size());
    }
  }

  private static ScanResult emptyResult(Path folder) {
    return new ScanResult(
        new AllowedRoot(folder), List.of(), List.of(), new ScanSummary(0, 0, 0, 0));
  }

  private static ScanResult rejectedResult(Path folder) {
    return new ScanResult(
        new AllowedRoot(folder),
        List.of(),
        List.of(
            new RejectedFile(
                folder.resolve("notes.txt"), "notes.txt", RejectionReason.UNSUPPORTED_EXTENSION)),
        new ScanSummary(0, 0, 0, 1));
  }
}
