package pl.pum.screensort.organization;

import java.net.URI;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.organization.review.ReviewStore;

@RestController
@RequestMapping("/api/organization/batches")
public class BatchController {
  private final BatchManager jobs;
  private final BatchStore store;

  public BatchController(BatchManager jobs, BatchStore store) {
    this.jobs = jobs; this.store = store;
  }

  @ModelAttribute
  public void preventCaching(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

  public record Request(String folderPath, boolean ocr, boolean visual) {}
  public record Action(String action) {}

  @PostMapping
  public ResponseEntity<BatchStore.Job> start(@RequestBody Request request) {
    if (request.folderPath() == null || request.folderPath().isBlank()) throw new AnalysisException(400, "Wybierz folder.");
    var job = jobs.start(request.folderPath(), request.ocr(), request.visual());
    return ResponseEntity.accepted().location(URI.create("/api/organization/batches/" + job.id()))
        .header("Cache-Control", "no-store").body(job);
  }

  @GetMapping("/{id}")
  public BatchStore.Job find(@PathVariable UUID id) {
    return store.find(id).orElseThrow(() -> new AnalysisException(404, "Zadanie nie istnieje."));
  }

  @GetMapping("/latest")
  public ResponseEntity<BatchStore.Job> latest(@RequestParam String folderPath) {
    String folder = jobs.canonicalFolder(folderPath);
    return store.latest(folder).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/{id}/items")
  public ReviewStore.Page items(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page) {
    if (page < 0 || page > 1_000_000) throw new AnalysisException(400, "Nieprawidłowa strona.");
    find(id);
    return store.items(id, page);
  }

  @GetMapping("/{id}/plan")
  public ReviewStore.PlanPage plan(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page) {
    if (page < 0 || page > 1_000_000) throw new AnalysisException(400, "Nieprawidłowa strona.");
    find(id);
    return store.plan(id, page);
  }

  @PostMapping("/{id}/control")
  public BatchStore.Job control(@PathVariable UUID id, @RequestBody Action request) {
    find(id);
    if ("RESUME".equals(request.action())) return jobs.resume(id);
    if (!"PAUSE".equals(request.action()) && !"CANCEL".equals(request.action())) {
      throw new AnalysisException(400, "Nieprawidłowe działanie.");
    }
    store.state(id, "PAUSE".equals(request.action()) ? "PAUSED" : "CANCELLED");
    return find(id);
  }
}
