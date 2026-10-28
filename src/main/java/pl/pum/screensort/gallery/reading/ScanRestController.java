package pl.pum.screensort.gallery.reading;

import jakarta.validation.Valid;
import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScanRestController {
  private final ScanJobManager scanJobs;

  public ScanRestController(ScanJobManager scanJobs) {
    this.scanJobs = scanJobs;
  }

  @PostMapping("/api/scan/jobs")
  public ResponseEntity<ScanJob> startJob(@Valid @RequestBody ScanRequest request) {
    ScanJob job = scanJobs.start(Path.of(request.folderPath()));
    return ResponseEntity.accepted()
        .location(URI.create("/api/scan/jobs/" + job.id()))
        .header("Cache-Control", "no-store")
        .body(job);
  }

  @GetMapping("/api/scan/jobs/{id}")
  public ResponseEntity<ScanJob> findJob(@PathVariable UUID id) {
    return scanJobs.find(id)
        .map(job -> ResponseEntity.ok().header("Cache-Control", "no-store").body(job))
        .orElseGet(() -> ResponseEntity.notFound().header("Cache-Control", "no-store").build());
  }

  @GetMapping("/api/scan/jobs/{id}/rejections")
  public ResponseEntity<ScanRejectionsDto> findRejections(@PathVariable UUID id) {
    var job = scanJobs.find(id);
    if (job.isEmpty()) {
      return ResponseEntity.notFound().header("Cache-Control", "no-store").build();
    }
    if (job.get().status() != ScanStatus.COMPLETED) {
      return ResponseEntity.status(HttpStatus.CONFLICT).header("Cache-Control", "no-store").build();
    }
    // Zadanie mogło wypaść z historii pomiędzy dwoma odczytami managera.
    return scanJobs.findRejections(id)
        .map(files -> {
          int total = job.get().summary().rejectedFiles();
          return ResponseEntity.ok().header("Cache-Control", "no-store")
              .body(new ScanRejectionsDto(
                  files.stream().map(RejectedFileDto::from).toList(), total, files.size() < total));
        })
        .orElseGet(() -> ResponseEntity.notFound().header("Cache-Control", "no-store").build());
  }
}
