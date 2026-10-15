package pl.pum.screensort.analysis.jobs;

import static pl.pum.screensort.analysis.jobs.AnalysisJob.Status.*;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.analysis.client.PythonAnalysisClient;
import pl.pum.screensort.analysis.database.AnalysisStore;
import pl.pum.screensort.analysis.result.AnalysisResult;
import pl.pum.screensort.analysis.result.SavedAnalysis;
import pl.pum.screensort.analysis.source.AnalysisSourceReader;

@Service
public class AnalysisJobManager {
  private final AnalysisSourceReader sources;
  private final PythonAnalysisClient python;
  private final AnalysisStore store;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Map<UUID, AnalysisJob> jobs = new LinkedHashMap<>();
  private UUID active;

  public AnalysisJobManager(AnalysisSourceReader sources, PythonAnalysisClient python, AnalysisStore store) {
    this.sources = sources; this.python = python; this.store = store;
  }

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

  private void run(UUID id, long fileId, boolean ocr, boolean visual) {
    try {
      var input = sources.read(fileId);
      update(id, ANALYZING, null, null);
      AnalysisResult result = python.analyze(input.bytes(), input.mediaType(), ocr, visual);
      if (!input.sha256().equals(result.sourceSha256())) throw new AnalysisException(502, "Nie zgadza się hash wyniku analizy.");
      if ((ocr == (result.ocr().status() == AnalysisResult.Status.SKIPPED))
          || (visual == (result.visual().status() == AnalysisResult.Status.SKIPPED))) {
        throw new AnalysisException(502, "Serwis zwrócił wynik niezgodny z zamówioną analizą.");
      }
      update(id, SAVING, null, null);
      var saved = store.save(fileId, ocr, visual, result);
      update(id, COMPLETED, saved, null);
    } catch (RuntimeException e) {
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
