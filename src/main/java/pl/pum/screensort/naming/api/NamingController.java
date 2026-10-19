package pl.pum.screensort.naming.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.pum.screensort.naming.NamingException;
import pl.pum.screensort.naming.NamingService;
import pl.pum.screensort.naming.api.dto.NamingReviewDto;

@RestController
@RequestMapping("/api/catalog/files/{fileId}/naming")
public class NamingController {
  private final NamingService naming;

  public NamingController(NamingService naming) { this.naming = naming; }

  public record Approval(long analysisId, String sourceSha256, long revision, String name) {}
  public record Error(String code, String message) {}

  @GetMapping
  public ResponseEntity<NamingReviewDto> read(@PathVariable long fileId) {
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(naming.read(fileId));
  }

  @PutMapping
  public ResponseEntity<NamingReviewDto> approve(@PathVariable long fileId, @RequestBody Approval request) {
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(naming.approve(
        fileId, request.analysisId(), request.sourceSha256(), request.revision(), request.name()));
  }

  @ExceptionHandler(NamingException.class)
  public ResponseEntity<Error> failure(NamingException error) {
    return ResponseEntity.status(error.status()).header("Cache-Control", "no-store")
        .body(new Error(error.code(), error.getMessage()));
  }
}
