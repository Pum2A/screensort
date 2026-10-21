package pl.pum.screensort.organization;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.analysis.AnalysisProcessor;
import pl.pum.screensort.scanning.folder.AllowedRoot;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;

class BatchManagerTest {
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
    when(processor.process(1, true, true, "a".repeat(64)))
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
    when(processor.process(anyLong(), anyBoolean(), anyBoolean(), anyString())).thenAnswer(call -> {
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
}
