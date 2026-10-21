package pl.pum.screensort.scanning.jobs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import pl.pum.screensort.diagnostics.RedactedThrowable;
import pl.pum.screensort.scanning.folder.InvalidScanFolderException;
import pl.pum.screensort.scanning.result.ScanResult;
import pl.pum.screensort.scanning.result.RejectedFile;
import pl.pum.screensort.scanning.result.ScanSummary;
import pl.pum.screensort.scanning.workflow.ScanWorkflow;

@Service
public class ScanJobManager {
  private static final Logger log = LoggerFactory.getLogger(ScanJobManager.class);
  private static final int MAX_REMEMBERED_JOBS = 20;
  private static final int MAX_REMEMBERED_REJECTIONS = 100;

  private final ScanWorkflow workflow;
  private final TaskExecutor jobRunner;
  // Dostęp do mapy i activeJobId jest zawsze w krótkich metodach synchronized.
  private final Map<UUID, ScanJob> jobs = new LinkedHashMap<>();
  private final Map<UUID, List<RejectedFile>> rejections = new LinkedHashMap<>();
  private UUID activeJobId;

  public ScanJobManager(
      ScanWorkflow workflow, @Qualifier("scanJobRunner") TaskExecutor jobRunner) {
    this.workflow = workflow;
    this.jobRunner = jobRunner;
  }

  /** Zwraca początkowy opis z ID, bez czekania na wykonanie workflow. */
  public ScanJob start(Path folder) {
    ScanJob job = reserve(folder);
    try {
      jobRunner.execute(
          () -> {
            try {
              run(job.id(), folder);
            } catch (IOException | RuntimeException exception) {
              // run już ustawił FAILED i zapisał przyczynę w logu.
              // Błąd zadania w tle nie jest odpowiedzią na zakończone żądanie POST.
            }
          });
    } catch (TaskRejectedException exception) {
      fail(job.id(), "Nie można teraz uruchomić skanu. Spróbuj ponownie później.");
      release(job.id());
      throw exception;
    }
    return job;
  }

  /** Zachowuje stary przepływ HTTP, ale korzysta z tej samej blokady skanów. */
  public ScanResult scanAndSave(Path folder) throws IOException {
    ScanJob job = reserve(folder);
    return run(job.id(), folder);
  }

  public synchronized Optional<ScanJob> find(UUID id) {
    return Optional.ofNullable(jobs.get(id));
  }

  public synchronized boolean hasActiveJob() { return activeJobId != null; }

  /** Próbka powodów odrzucenia jest dostępna dopiero po udanym zakończeniu. */
  public synchronized Optional<List<RejectedFile>> findRejections(UUID id) {
    return Optional.ofNullable(rejections.get(id));
  }

  private synchronized ScanJob reserve(Path folder) {
    Objects.requireNonNull(folder);
    if (activeJobId != null) {
      throw new ScanAlreadyRunningException();
    }
    ScanJob job =
        new ScanJob(
            UUID.randomUUID(),
            folder.toString(),
            ScanStatus.PREPARING,
            new ScanSummary(0, 0, 0, 0),
            null);
    if (jobs.size() == MAX_REMEMBERED_JOBS) {
      UUID oldestId = jobs.keySet().iterator().next();
      jobs.remove(oldestId);
      rejections.remove(oldestId);
    }
    jobs.put(job.id(), job);
    activeJobId = job.id();
    return job;
  }

  private ScanResult run(UUID id, Path folder) throws IOException {
    try {
      ScanResult result =
          workflow.scanAndSave(folder, (status, summary) -> update(id, status, summary));
      // Workflow wraca dopiero po zakończeniu transakcji CatalogUpdater.
      complete(id, result);
      return result;
    } catch (IOException | RuntimeException exception) {
      String message =
          exception instanceof InvalidScanFolderException
              ? exception.getMessage()
              : "Skanowanie lub zapis katalogu nie powiodły się. Szczegóły są w logu backendu.";
      fail(id, message);
      log.warn("Nie udało się zakończyć zadania skanu {}", id, RedactedThrowable.of(exception));
      if (exception instanceof InvalidScanFolderException) throw exception;
      // Oryginał trafiłby do logu kontenera razem ze ścieżką z komunikatu IO.
      if (exception instanceof IOException) {
        throw new IOException("Scan failed", RedactedThrowable.of(exception));
      }
      throw new IllegalStateException("Scan failed", RedactedThrowable.of(exception));
    } finally {
      release(id);
    }
  }

  private synchronized void update(UUID id, ScanStatus status, ScanSummary summary) {
    ScanJob previous = jobs.get(id);
    jobs.put(id, new ScanJob(id, previous.folderPath(), status, summary, null));
  }

  private synchronized void complete(UUID id, ScanResult result) {
    // Nie zatrzymujemy pełnego ScanResult ani tysięcy ścieżek w historii zadań.
    rejections.put(id, result.rejected().stream().limit(MAX_REMEMBERED_REJECTIONS).toList());
    update(id, ScanStatus.COMPLETED, result.summary());
  }

  private synchronized void fail(UUID id, String message) {
    ScanJob previous = jobs.get(id);
    jobs.put(
        id,
        new ScanJob(id, previous.folderPath(), ScanStatus.FAILED, previous.summary(), message));
  }

  private synchronized void release(UUID id) {
    if (id.equals(activeJobId)) {
      activeJobId = null;
    }
  }
}
