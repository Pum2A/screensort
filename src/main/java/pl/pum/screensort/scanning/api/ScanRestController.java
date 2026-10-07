package pl.pum.screensort.scanning.api;

import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.pum.screensort.scanning.api.dto.ScanRequest;
import pl.pum.screensort.scanning.api.dto.ScanResultDto;
import pl.pum.screensort.scanning.api.dto.ScanRejectionsDto;
import pl.pum.screensort.scanning.api.dto.RejectedFileDto;
import pl.pum.screensort.scanning.folder.InvalidScanFolderException;
import pl.pum.screensort.scanning.jobs.ScanAlreadyRunningException;
import pl.pum.screensort.scanning.jobs.ScanJob;
import pl.pum.screensort.scanning.jobs.ScanJobManager;
import pl.pum.screensort.scanning.jobs.ScanStatus;
import pl.pum.screensort.scanning.result.ScanResult;

@RestController
public class ScanRestController {
  private final ScanJobManager scanJobs;

  public ScanRestController(ScanJobManager scanJobs) {
    this.scanJobs = scanJobs;
  }

  @PostMapping("/api/scan")
  public ScanResultDto scan(@Valid @RequestBody ScanRequest request) throws IOException {
    ScanResult result = scanJobs.scanAndSave(Path.of(request.folderPath()));
    return ScanResultDto.from(result);
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

  @ExceptionHandler(ScanAlreadyRunningException.class)
  public ResponseEntity<String> handleBusy(ScanAlreadyRunningException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
  }

  @ExceptionHandler(TaskRejectedException.class)
  public ResponseEntity<String> handleUnavailable(TaskRejectedException exception) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body("Nie można teraz uruchomić skanu. Spróbuj ponownie później.");
  }

  @ExceptionHandler(InvalidPathException.class)
  public ResponseEntity<String> handleInvalidPath(InvalidPathException exception) {
    return ResponseEntity.badRequest().body("Ścieżka ma niepoprawny format.");
  }

  @ExceptionHandler(InvalidScanFolderException.class)
  public ResponseEntity<String> handleInvalidFolder(InvalidScanFolderException e) {
    return ResponseEntity.badRequest().body(e.getMessage()); // 400
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<String> handleValidation(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest()
        .body(e.getBindingResult().getFieldError().getDefaultMessage());
  }
}
