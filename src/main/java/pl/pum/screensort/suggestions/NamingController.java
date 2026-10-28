package pl.pum.screensort.suggestions;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog/files/{fileId}/naming")
public class NamingController {
  private final NamingService naming;

  public NamingController(NamingService naming) { this.naming = naming; }

  public record Approval(long analysisId, String sourceSha256, long revision, String name) {}

  @GetMapping
  public ResponseEntity<NamingReviewDto> read(@PathVariable long fileId) {
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(naming.read(fileId));
  }

  @PutMapping
  public ResponseEntity<NamingReviewDto> approve(@PathVariable long fileId, @RequestBody Approval request) {
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(naming.approve(
        fileId, request.analysisId(), request.sourceSha256(), request.revision(), request.name()));
  }
}
