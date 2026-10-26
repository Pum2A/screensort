package pl.pum.screensort.organization.review;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organization/batches/{batchId}/items/{fileId}/review")
public class ReviewController {
  private final ReviewService reviews;

  public ReviewController(ReviewService reviews) { this.reviews = reviews; }

  @GetMapping
  public ResponseEntity<ReviewItem> read(@PathVariable UUID batchId, @PathVariable long fileId) {
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(reviews.read(batchId, fileId));
  }

  @PutMapping
  public ResponseEntity<ReviewItem> save(@PathVariable UUID batchId, @PathVariable long fileId,
      @RequestBody ReviewService.Request request) {
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(reviews.save(batchId, fileId, request));
  }
}
