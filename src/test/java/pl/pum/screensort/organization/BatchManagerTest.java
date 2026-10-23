package pl.pum.screensort.organization;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.analysis.AnalysisProcessor;
import pl.pum.screensort.analysis.EngineBusyException;
import pl.pum.screensort.scanning.folder.AllowedRoot;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;

class BatchManagerTest {
  @Test void resumeReportsConflictWhenTheDatabaseAlreadyRunsAnotherBatch() {
    var store = mock(BatchStore.class);
    var processor = mock(AnalysisProcessor.class);
    var folders = mock(ScanFolderValidator.class);
    var id = UUID.randomUUID();
    // Unikalny indeks ux_analysis_batch_active nie dopuszcza drugiego aktywnego batchu.
    when(store.resume(id)).thenThrow(new DataIntegrityViolationException("ux_analysis_batch_active"));
    var manager = new BatchManager(store, processor, folders);
    try {
      assertThatThrownBy(() -> manager.resume(id))
          .isInstanceOfSatisfying(AnalysisException.class,
              error -> assertThat(error.status()).isEqualTo(409));
    } finally { manager.stop(); }
  }

  @Test void unavailableEnginePausesInsteadOfFailingThousandsOfFiles() throws Exception {
    var store = mock(BatchStore.class);
    var processor = mock(AnalysisProcessor.class);
    var folders = mock(ScanFolderValidator.class);
    var folder = Path.of("synthetic-test").toAbsolutePath();
    when(folders.validate(folder)).thenReturn(new AllowedRoot(folder));
    var job = new BatchStore.Job(UUID.randomUUID(), folder.toString(), "RUNNING", true, true, 10000, 0, 0, 0, Instant.now());
    when(store.create(folder.toString(), true, true)).thenReturn(job);
    when(store.isRunning(job.id())).thenReturn(true);
    when(store.next(job.id())).thenReturn(Optional.of(new BatchStore.Pending(1, "a.png", "a".repeat(64))));
    when(processor.process(1, true, true, "a".repeat(64), false))
        .thenThrow(new AnalysisException(503, "Unavailable"));
    var done = new CountDownLatch(1);
    doAnswer(call -> { done.countDown(); return null; }).when(store).release(job.id());
    var manager = new BatchManager(store, processor, folders);
    try {
      assertThat(manager.start(folder.toString(), true, true).id()).isEqualTo(job.id());
      assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      verify(store).failed(job.id(), 1, "Unavailable", true);
      verify(store).state(job.id(), "PAUSED");
      verify(store, times(1)).next(job.id());
    } finally { manager.stop(); }
  }

  @Test void returnsTicketBeforeTheModelFinishesAndRejectsDuplicateStarts() throws Exception {
    var store = mock(BatchStore.class);
    var processor = mock(AnalysisProcessor.class);
    var folders = mock(ScanFolderValidator.class);
    var folder = Path.of("synthetic-test").toAbsolutePath();
    when(folders.validate(folder)).thenReturn(new AllowedRoot(folder));
    var job = new BatchStore.Job(UUID.randomUUID(), folder.toString(), "RUNNING", true, true, 1, 0, 0, 0, Instant.now());
    when(store.create(folder.toString(), true, true)).thenReturn(job);
    when(store.isRunning(job.id())).thenReturn(true, false);
    when(store.next(job.id())).thenReturn(Optional.of(new BatchStore.Pending(1, "a.png", "a".repeat(64))));
    var started = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    var done = new CountDownLatch(1);
    when(processor.process(anyLong(), anyBoolean(), anyBoolean(), anyString(), anyBoolean())).thenAnswer(call -> {
      started.countDown();
      if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("Test timeout");
      throw new AnalysisException(409, "Source changed");
    });
    doAnswer(call -> { done.countDown(); return null; }).when(store).release(job.id());
    var manager = new BatchManager(store, processor, folders);
    try {
      assertThat(manager.start(folder.toString(), true, true).id()).isEqualTo(job.id());
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> manager.start(folder.toString(), true, true)).isInstanceOf(AnalysisException.class);
      finish.countDown();
      assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      verify(store).failed(job.id(), 1, "Source changed", false);
    } finally { finish.countDown(); manager.stop(); }
  }

  @Test void aCorruptImageFailsAloneAndTheFolderFinishes() throws Exception {
    var batch = new Batch();
    batch.queue(new BatchStore.Pending(1, "broken.png", "a".repeat(64)), new BatchStore.Pending(2, "ok.png", "b".repeat(64)));
    when(batch.processor.process(1, true, true, "a".repeat(64), false))
        .thenThrow(new AnalysisException(422, "Bad image"));
    batch.run();
    verify(batch.store).failed(batch.job.id(), 1, "Bad image", false);
    verify(batch.store).completed(eq(batch.job.id()), eq(new BatchStore.Pending(2, "ok.png", "b".repeat(64))), any());
    verify(batch.store).state(batch.job.id(), "COMPLETED");
    verify(batch.store, never()).state(batch.job.id(), "PAUSED");
  }

  @Test void aTimeoutGoesToTheEndOnceThenGetsALongerTry() throws Exception {
    var batch = new Batch();
    var first = new BatchStore.Pending(1, "slow.png", "a".repeat(64));
    var retry = new BatchStore.Pending(1, "slow.png", "a".repeat(64), 1);
    batch.queue(first, retry);
    when(batch.processor.process(1, true, true, "a".repeat(64), false))
        .thenThrow(new AnalysisException(504, "Timeout"));
    when(batch.processor.process(1, true, true, "a".repeat(64), true))
        .thenThrow(new AnalysisException(504, "Timeout"));
    batch.run();
    verify(batch.store).postpone(batch.job.id(), 1, BatchManager.RETRY_LATER);
    verify(batch.processor).process(1, true, true, "a".repeat(64), true);
    verify(batch.store).failed(batch.job.id(), 1, BatchManager.TIMED_OUT_TWICE, false);
    verify(batch.store).state(batch.job.id(), "COMPLETED");
  }

  @Test void aBusyEngineIsWaitedForInsteadOfPausingTheFolder() throws Exception {
    var batch = new Batch();
    var item = new BatchStore.Pending(1, "a.png", "a".repeat(64));
    batch.queue(item, item);
    when(batch.processor.process(1, true, true, "a".repeat(64), false))
        .thenThrow(new EngineBusyException(Duration.ofSeconds(3)))
        .thenReturn(null);
    batch.run();
    assertThat(batch.pauses).containsExactly(Duration.ofSeconds(3));
    verify(batch.store).completed(batch.job.id(), item, null);
    verify(batch.store, never()).state(batch.job.id(), "PAUSED");
  }

  @Test void anEngineBusyForTooLongPausesTheFolder() throws Exception {
    var batch = new Batch();
    var item = new BatchStore.Pending(1, "a.png", "a".repeat(64));
    when(batch.store.next(batch.job.id())).thenReturn(Optional.of(item));
    when(batch.processor.process(1, true, true, "a".repeat(64), false))
        .thenThrow(new EngineBusyException(Duration.ofSeconds(30)));
    batch.run();
    assertThat(batch.pauses).hasSize((int) (BatchManager.MAX_BUSY_WAIT.toSeconds() / 30));
    verify(batch.store).state(batch.job.id(), "PAUSED");
  }

  /** A started folder job with mocked store and processor; run() returns when the worker stops. */
  private static final class Batch {
    final BatchStore store = mock(BatchStore.class);
    final AnalysisProcessor processor = mock(AnalysisProcessor.class);
    final ScanFolderValidator folders = mock(ScanFolderValidator.class);
    final Path folder = Path.of("synthetic-test").toAbsolutePath();
    final BatchStore.Job job = new BatchStore.Job(UUID.randomUUID(), folder.toString(), "RUNNING", true, true, 2, 0, 0, 0, Instant.now());
    final java.util.List<Duration> pauses = new java.util.concurrent.CopyOnWriteArrayList<>();

    Batch() throws Exception {
      when(folders.validate(folder)).thenReturn(new AllowedRoot(folder));
      when(store.create(folder.toString(), true, true)).thenReturn(job);
      when(store.isRunning(job.id())).thenReturn(true);
    }

    void queue(BatchStore.Pending... items) {
      var answers = new java.util.ArrayList<Optional<BatchStore.Pending>>();
      for (int i = 1; i < items.length; i++) answers.add(Optional.of(items[i]));
      answers.add(Optional.empty());
      when(store.next(job.id())).thenReturn(Optional.of(items[0]), answers.toArray(Optional[]::new));
    }

    void run() throws Exception {
      var done = new CountDownLatch(1);
      doAnswer(call -> { done.countDown(); return null; }).when(store).release(job.id());
      var manager = new BatchManager(store, processor, folders, pauses::add);
      try {
        manager.start(folder.toString(), true, true);
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      } finally { manager.stop(); }
    }
  }
}
