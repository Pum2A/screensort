package pl.pum.screensort.analysis.api;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.analysis.database.AnalysisStore;
import pl.pum.screensort.analysis.jobs.AnalysisJob;
import pl.pum.screensort.analysis.jobs.AnalysisJobManager;
import pl.pum.screensort.analysis.result.SavedAnalysis;

@RestController
public class AnalysisController {
  private final AnalysisJobManager jobs;
  private final AnalysisStore store;
  public AnalysisController(AnalysisJobManager jobs, AnalysisStore store) { this.jobs = jobs; this.store = store; }

  public record Request(long fileId, boolean ocr, boolean visual) {}

  @PostMapping("/api/analysis/jobs")
  public ResponseEntity<AnalysisJob> start(@RequestBody Request request) {
    var job = jobs.start(request.fileId(), request.ocr(), request.visual());
    return ResponseEntity.accepted().location(URI.create("/api/analysis/jobs/" + job.id()))
        .header("Cache-Control", "no-store").body(job);
  }

  @GetMapping("/api/analysis/jobs/{id}")
  public ResponseEntity<AnalysisJob> job(@PathVariable UUID id) {
    return jobs.find(id).map(value -> ResponseEntity.ok().header("Cache-Control", "no-store").body(value))
        .orElseGet(() -> ResponseEntity.notFound().header("Cache-Control", "no-store").build());
  }

  @GetMapping("/api/catalog/files/{id}/analysis")
  public ResponseEntity<SavedAnalysis> latest(@PathVariable long id) {
    return store.latest(id).map(value -> ResponseEntity.ok().header("Cache-Control", "no-store").body(value))
        .orElseGet(() -> ResponseEntity.notFound().header("Cache-Control", "no-store").build());
  }

  @ExceptionHandler(AnalysisException.class)
  public ResponseEntity<String> failure(AnalysisException e) {
    return ResponseEntity.status(e.status()).header("Cache-Control", "no-store").body(e.getMessage());
  }
}
