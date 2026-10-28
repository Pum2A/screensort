package pl.pum.screensort.export;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organization")
public class ExportController {
  public record Request(String destinationFolder) {}
  private final ExportManager exports;
  private final ExportStore store;

  public ExportController(ExportManager exports, ExportStore store) { this.exports = exports; this.store = store; }

  @PostMapping("/batches/{batchId}/exports")
  public ResponseEntity<ExportStore.Job> start(@PathVariable UUID batchId, @RequestBody Request request) {
    var job = exports.start(batchId, request.destinationFolder());
    return ResponseEntity.accepted().location(URI.create("/api/organization/exports/" + job.id()))
        .header("Cache-Control", "no-store").body(job);
  }

  @GetMapping("/batches/{batchId}/exports/latest")
  public ResponseEntity<ExportStore.Job> latest(@PathVariable UUID batchId) {
    return store.latest(batchId).map(job -> ResponseEntity.ok().header("Cache-Control", "no-store").body(job))
        .orElseGet(() -> ResponseEntity.notFound().header("Cache-Control", "no-store").build());
  }

  @GetMapping("/exports/{id}")
  public ResponseEntity<ExportStore.Job> read(@PathVariable UUID id) {
    var job = store.find(id).orElseThrow(() -> new ExportException(404, "EXPORT_NOT_FOUND", "Nie znaleziono zadania kopiowania."));
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(job);
  }

  @GetMapping("/exports/{id}/issues")
  public ResponseEntity<ExportStore.Issues> issues(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page) {
    if (page < 0 || page > 1_000_000) throw new ExportException(400, "INVALID_PAGE", "Nieprawidłowa strona.");
    read(id);
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(store.issues(id, page));
  }
}
