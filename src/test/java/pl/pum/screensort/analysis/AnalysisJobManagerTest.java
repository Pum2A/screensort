package pl.pum.screensort.analysis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import pl.pum.screensort.diagnostics.ProductMetrics;
import pl.pum.screensort.analysis.client.PythonAnalysisClient;
import pl.pum.screensort.analysis.database.AnalysisStore;
import pl.pum.screensort.analysis.jobs.AnalysisJob;
import pl.pum.screensort.analysis.jobs.AnalysisJobManager;
import pl.pum.screensort.analysis.result.SavedAnalysis;
import pl.pum.screensort.analysis.source.AnalysisSourceReader;

class AnalysisJobManagerTest {
  @Test void returnsIdBeforeWorkAndCompletesOnlyAfterSaving() throws Exception {
    var source = mock(AnalysisSourceReader.class);
    var python = mock(PythonAnalysisClient.class);
    var store = mock(AnalysisStore.class);
    var started = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    var result = AnalysisResultTest.result("a".repeat(64));
    when(source.read(1)).thenReturn(new AnalysisSourceReader.Input(1, new byte[]{1}, result.sourceSha256(), "image/png"));
    when(python.analyze(any(), anyString(), eq(true), eq(false), eq(false))).thenAnswer(call -> {
      started.countDown();
      if (!finish.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test timeout");
      return result;
    });
    var saved = new SavedAnalysis(1, 1, Instant.now(), true, result);
    when(store.save(1, true, false, result)).thenReturn(saved);
    var manager = new AnalysisJobManager(new AnalysisProcessor(source, python, store,
        new ProductMetrics(new SimpleMeterRegistry())));
    try {
      var job = manager.start(1, true, false);
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(manager.find(job.id()).orElseThrow().status()).isEqualTo(AnalysisJob.Status.ANALYZING);
      assertThatThrownBy(() -> manager.start(2, true, true)).isInstanceOf(AnalysisException.class);
      finish.countDown();
      AnalysisJob completed = awaitTerminal(manager, job);
      assertThat(completed.status()).isEqualTo(AnalysisJob.Status.COMPLETED);
      assertThat(completed.analysis()).isEqualTo(saved);
      verify(store).save(1, true, false, result);
    } finally { finish.countDown(); manager.stop(); }
  }

  @Test void rejectsHashMismatchWithoutSaving() throws Exception {
    var source = mock(AnalysisSourceReader.class);
    var python = mock(PythonAnalysisClient.class);
    var store = mock(AnalysisStore.class);
    when(source.read(1)).thenReturn(new AnalysisSourceReader.Input(1, new byte[]{1}, "a".repeat(64), "image/png"));
    when(python.analyze(any(), anyString(), eq(true), eq(false), eq(false)))
        .thenReturn(AnalysisResultTest.result("b".repeat(64)));
    var manager = new AnalysisJobManager(new AnalysisProcessor(source, python, store,
        new ProductMetrics(new SimpleMeterRegistry())));
    try {
      var terminal = awaitTerminal(manager, manager.start(1, true, false));
      assertThat(terminal.status()).isEqualTo(AnalysisJob.Status.FAILED);
      assertThat(terminal.errorMessage()).contains("hash");
      verifyNoInteractions(store);
    } finally { manager.stop(); }
  }

  private AnalysisJob awaitTerminal(AnalysisJobManager manager, AnalysisJob job) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      var current = manager.find(job.id()).orElseThrow();
      if (current.status() == AnalysisJob.Status.COMPLETED || current.status() == AnalysisJob.Status.FAILED) return current;
      Thread.sleep(10);
    }
    throw new AssertionError("Job did not complete");
  }
}
