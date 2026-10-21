package pl.pum.screensort.organization.export;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import pl.pum.screensort.organization.BatchStore;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;

@Service
public class ExportManager {
  private static final Logger log = LoggerFactory.getLogger(ExportManager.class);

  private final ExportStore store;
  private final BatchStore batches;
  private final OrganizedFileCopier copier;
  private final ScanFolderValidator folders;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicBoolean active = new AtomicBoolean();

  public ExportManager(ExportStore store, BatchStore batches, OrganizedFileCopier copier, ScanFolderValidator folders) {
    this.store = store; this.batches = batches; this.copier = copier; this.folders = folders;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() { store.recover(); }

  public boolean hasActiveJob() { return active.get(); }

  public ExportStore.Job start(UUID batchId, String destinationFolder) {
    if (destinationFolder == null || destinationFolder.isBlank()) {
      throw new ExportException(400, "DESTINATION_REQUIRED", "Wybierz folder dla uporządkowanej kopii.");
    }
    var batch = batches.find(batchId).orElseThrow(() -> new ExportException(404, "BATCH_NOT_FOUND", "Analiza folderu nie istnieje."));
    if (!"COMPLETED".equals(batch.status())) throw new ExportException(409, "BATCH_RUNNING", "Poczekaj na zakończenie analizy folderu.");
    if (!active.compareAndSet(false, true)) throw new ExportException(409, "EXPORT_BUSY", "Kopiowanie jest już w toku.");
    UUID id = UUID.randomUUID();
    Path output = null;
    boolean saved = false;
    try {
      Path destination = folders.validate(Path.of(destinationFolder)).path();
      if (!Files.isWritable(destination)) throw new ExportException(400, "DESTINATION_UNAVAILABLE", "Nie można zapisywać w wybranym folderze.");
      output = newOutputFolder(destination);
      var job = store.create(id, batchId, output.toString());
      saved = true;
      Path target = output;
      worker.execute(() -> run(id, target));
      return job;
    } catch (IOException | InvalidPathException error) {
      throw new ExportException(400, "DESTINATION_UNAVAILABLE", "Nie udało się przygotować folderu wynikowego. Wybierz dostępny folder.");
    } catch (DataIntegrityViolationException error) {
      throw new ExportException(409, "EXPORT_BUSY", "Inne kopiowanie jest już w toku. Odśwież jego stan.");
    } catch (RuntimeException error) {
      if (saved) {
        store.finish(id, "INTERRUPTED", "Nie udało się uruchomić kopiowania. Spróbuj ponownie.");
        active.set(false);
      }
      throw error;
    } finally {
      if (!saved) {
        active.set(false);
        if (output != null) {
          try { Files.delete(output); } catch (IOException ignored) { /* Only an empty, owned directory can be removed. */ }
        }
      }
    }
  }

  private void run(UUID id, Path output) {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        var next = store.next(id);
        if (next.isEmpty()) {store.finish(id, "COMPLETED", null); return;}
        var item = next.orElseThrow();
        if (!store.current(item)) {
          store.failed(id, item.fileId(), "Decyzja lub zawartość pliku zmieniła się. Sprawdź propozycję ponownie.");
          continue;
        }
        try {store.copied(id, item.fileId(), copier.copy(item, output));}
        catch (InterruptedIOException error) {Thread.currentThread().interrupt(); break;}
        catch (OrganizedFileCopier.CopyProblem error) {store.failed(id, item.fileId(), error.getMessage());}
        catch (IOException | IllegalArgumentException error) {
          store.failed(id, item.fileId(), "Nie udało się odczytać lub zapisać obrazu. Sprawdź dostęp do folderów.");
        }
      }
      store.finish(id, "INTERRUPTED", "Kopiowanie zostało przerwane. Zapisane kopie pozostały w folderze wynikowym.");
    } catch (RuntimeException error) {
      log.error("Organized copy failed: export={}", id, error);
      store.finish(id, "INTERRUPTED", "Nie udało się dokończyć kopiowania. Zapisane kopie pozostały w folderze wynikowym.");
    } finally { active.set(false); }
  }

  private static Path newOutputFolder(Path destination) throws IOException {
    for (int index = 1; index <= 10_000; index++) {
      Path output = destination.resolve(index == 1 ? "ScreenSort" : "ScreenSort (" + index + ")");
      try {return Files.createDirectory(output);}
      catch (FileAlreadyExistsException ignored) { /* Every export gets a separate directory. */ }
    }
    throw new IOException("No output directory available");
  }

  @PreDestroy public void close() { worker.shutdownNow(); }
}
