package pl.pum.screensort.organization;

import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.analysis.AnalysisProcessor;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;

@Service
public class BatchManager {
  private static final Logger log = LoggerFactory.getLogger(BatchManager.class);

  private final BatchStore store;
  private final AnalysisProcessor processor;
  private final ScanFolderValidator folders;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private UUID active;

  public BatchManager(BatchStore store, AnalysisProcessor processor, ScanFolderValidator folders) {
    this.store = store; this.processor = processor; this.folders = folders;
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

  private void run(BatchStore.Job job) {
    try {
      while (!Thread.currentThread().isInterrupted()
          && store.isRunning(job.id())) {
        var pending = store.next(job.id());
        if (pending.isEmpty()) { store.state(job.id(), "COMPLETED"); break; }
        var item = pending.orElseThrow();
        try {
          var saved = processor.process(item.fileId(), job.ocr(), job.visual(), item.sourceSha256());
          store.completed(job.id(), item, saved);
        } catch (AnalysisException error) {
          boolean unavailable = error.status() == 503;
          store.failed(job.id(), item.fileId(), error.getMessage(), unavailable);
          if (unavailable) { store.state(job.id(), "PAUSED"); break; }
        } catch (RuntimeException error) {
          store.failed(job.id(), item.fileId(), "Nie udało się przetworzyć obrazu. Oryginał pozostaje bez zmian.", false);
        }
      }
    } catch (RuntimeException error) {
      log.error("Folder analysis failed and was paused: job={}", job.id(), error);
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
