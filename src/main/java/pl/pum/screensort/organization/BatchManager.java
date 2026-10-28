package pl.pum.screensort.organization;

import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.analysis.AnalysisProcessor;
import pl.pum.screensort.analysis.EngineBusyException;
import pl.pum.screensort.diagnostics.RedactedThrowable;
import pl.pum.screensort.gallery.reading.ScanFolderValidator;

@Service
public class BatchManager {
  private static final Logger log = LoggerFactory.getLogger(BatchManager.class);

  // After a timeout Python keeps working on the old image for a while; this is how long we wait.
  static final Duration MAX_BUSY_WAIT = Duration.ofMinutes(5);
  static final String RETRY_LATER = "Analiza trwała zbyt długo. Spróbujemy jeszcze raz na końcu, z dłuższym czasem.";
  static final String TIMED_OUT_TWICE = "Analiza tego obrazu trwała zbyt długo, także przy wydłużonym czasie.";

  @FunctionalInterface
  interface Pause { void sleep(Duration duration) throws InterruptedException; }

  private final BatchStore store;
  private final AnalysisProcessor processor;
  private final ScanFolderValidator folders;
  private final Pause pause;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private UUID active;

  @Autowired
  public BatchManager(BatchStore store, AnalysisProcessor processor, ScanFolderValidator folders) {
    this(store, processor, folders, duration -> Thread.sleep(duration));
  }

  BatchManager(BatchStore store, AnalysisProcessor processor, ScanFolderValidator folders, Pause pause) {
    this.store = store; this.processor = processor; this.folders = folders; this.pause = pause;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() { store.recover(); }

  public synchronized BatchStore.Job start(String folder, boolean ocr, boolean visual) {
    if (active != null) throw new AnalysisException(409, "Inna analiza folderu jest w toku.");
    if (!ocr && !visual) throw new AnalysisException(400, "Wybierz co najmniej jeden rodzaj analizy.");
    String root = canonicalFolder(folder);
    try {
      var job = store.create(root, ocr, visual);
      launch(job);
      return job;
    } catch (DataIntegrityViolationException error) {
      throw new AnalysisException(409, "Inna analiza folderu jest w toku.");
    }
  }

  public String canonicalFolder(String folder) {
    if (folder == null || folder.isBlank() || folder.length() > 8192) {
      throw new AnalysisException(400, "Wybierz prawidłowy folder.");
    }
    try { return folders.validate(Path.of(folder)).path().toString(); }
    catch (IOException | IllegalArgumentException error) {
      throw new AnalysisException(400, "Nie można odczytać wybranego folderu. Sprawdź lokalizację i uprawnienia.");
    }
  }

  public synchronized BatchStore.Job resume(UUID id) {
    if (active != null) throw new AnalysisException(409, "Poczekaj na zakończenie bieżącego obrazu.");
    BatchStore.Job job;
    try {
      job = store.resume(id);
    } catch (DataIntegrityViolationException error) {
      // Unikalny indeks dopuszcza tylko jeden aktywny batch w całej bazie.
      throw new AnalysisException(409, "Inna analiza folderu jest w toku.");
    }
    if (!job.status().equals("RUNNING")) throw new AnalysisException(409, "Tego zadania nie można wznowić.");
    launch(job);
    return job;
  }

  private void launch(BatchStore.Job job) {
    active = job.id();
    try { worker.submit(() -> run(job)); }
    catch (RuntimeException error) { active = null; store.state(job.id(), "PAUSED"); throw error; }
  }

  /*
   * What a failed image does to the batch:
   * - bad image (422) and other file problems: only this file fails, the batch goes on;
   * - timeout (504): first time to the end of the queue, then one longer try, then it fails;
   * - busy engine: wait and try the same file again, pause only after MAX_BUSY_WAIT;
   * - engine unavailable (503): the file goes back to the queue and the batch pauses.
   */
  private void run(BatchStore.Job job) {
    Duration busyWaited = Duration.ZERO;
    try {
      while (!Thread.currentThread().isInterrupted()
          && store.isRunning(job.id())) {
        var pending = store.next(job.id());
        if (pending.isEmpty()) { store.state(job.id(), "COMPLETED"); break; }
        var item = pending.orElseThrow();
        try {
          // A file analyzed before (earlier batch, app restart) keeps its result instead of costing seconds again.
          var reused = processor.reusable(item.fileId(), job.ocr(), job.visual(), item.sourceSha256());
          var saved = reused.isPresent() ? reused.get()
              : processor.process(item.fileId(), job.ocr(), job.visual(), item.sourceSha256(), item.attempts() > 0);
          store.completed(job.id(), item, saved);
          busyWaited = Duration.ZERO;
        } catch (EngineBusyException busy) {
          store.failed(job.id(), item.fileId(), busy.getMessage(), true);
          if (busyWaited.compareTo(MAX_BUSY_WAIT) >= 0) { store.state(job.id(), "PAUSED"); break; }
          try { pause.sleep(busy.retryAfter()); }
          catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
          busyWaited = busyWaited.plus(busy.retryAfter());
        } catch (AnalysisException error) {
          if (error.status() == 504 && item.attempts() == 0) {
            store.postpone(job.id(), item.fileId(), RETRY_LATER);
            continue;
          }
          boolean unavailable = error.status() == 503;
          String message = error.status() == 504 ? TIMED_OUT_TWICE : error.getMessage();
          store.failed(job.id(), item.fileId(), message, unavailable);
          if (unavailable) { store.state(job.id(), "PAUSED"); break; }
        } catch (RuntimeException error) {
          store.failed(job.id(), item.fileId(), "Nie udało się przetworzyć obrazu. Oryginał pozostaje bez zmian.", false);
        }
      }
    } catch (RuntimeException error) {
      log.error("Folder analysis failed and was paused: job={}", job.id(), RedactedThrowable.of(error));
      store.state(job.id(), "PAUSED");
    } finally {
      try { store.release(job.id()); }
      finally { synchronized (this) { if (job.id().equals(active)) active = null; } }
    }
  }

  @PreDestroy
  public void stop() { worker.shutdownNow(); }
  public synchronized boolean hasActiveJob() { return active != null; }
}
