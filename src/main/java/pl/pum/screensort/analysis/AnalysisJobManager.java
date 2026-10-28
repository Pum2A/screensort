package pl.pum.screensort.analysis;

import static pl.pum.screensort.analysis.AnalysisJob.Status.*;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.pum.screensort.diagnostics.RedactedThrowable;

@Service
public class AnalysisJobManager {
  private static final Logger log = LoggerFactory.getLogger(AnalysisJobManager.class);

  private final AnalysisProcessor processor;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Map<UUID, AnalysisJob> jobs = new LinkedHashMap<>();
  private UUID active;

  public AnalysisJobManager(AnalysisProcessor processor) { this.processor = processor; }

  public synchronized AnalysisJob start(long fileId, boolean ocr, boolean visual) {
    if (fileId <= 0 || (!ocr && !visual)) throw new AnalysisException(400, "Wybierz obraz i rodzaj analizy.");
    if (active != null) throw new AnalysisException(409, "Inna analiza jest już w toku.");
    var job = new AnalysisJob(UUID.randomUUID(), fileId, PREPARING, Instant.now(), null, null);
    if (jobs.size() >= 20) jobs.remove(jobs.keySet().iterator().next());
    jobs.put(job.id(), job);
    active = job.id();
    try { worker.submit(() -> run(job.id(), fileId, ocr, visual)); }
    catch (RuntimeException e) { active = null; jobs.remove(job.id()); throw e; }
    return job;
  }

  public synchronized Optional<AnalysisJob> find(UUID id) { return Optional.ofNullable(jobs.get(id)); }
  public synchronized boolean hasActiveJob() { return active != null; }

  private void run(UUID id, long fileId, boolean ocr, boolean visual) {
    try {
      update(id, ANALYZING, null, null);
      var saved = processor.process(fileId, ocr, visual, null, () -> update(id, SAVING, null, null));
      update(id, COMPLETED, saved, null);
    } catch (RuntimeException e) {
      // Oczekiwane błędy analizy (zmiana pliku, brak silnika) trafiają do statusu zadania.
      if (e instanceof AnalysisException) {
        log.warn("Image analysis stopped: job={} file={} reason={}", id, fileId, e.getMessage());
      } else {
        log.error("Image analysis failed: job={} file={}", id, fileId, RedactedThrowable.of(e));
      }
      String message = e instanceof AnalysisException ? e.getMessage()
          : "Analiza lub zapis wyniku nie powiodły się. Oryginał pozostaje bez zmian.";
      update(id, FAILED, null, message);
    } finally {
      synchronized (this) { if (id.equals(active)) active = null; }
    }
  }

  private synchronized void update(UUID id, AnalysisJob.Status status, SavedAnalysis result, String message) {
    var previous = jobs.get(id);
    jobs.put(id, new AnalysisJob(id, previous.fileId(), status, previous.startedAt(), result, message));
  }

  @PreDestroy
  public void stop() { worker.shutdownNow(); }
}
