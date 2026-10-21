package pl.pum.screensort.organization.review;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.pum.screensort.naming.NamingException;

@RestController
@RequestMapping("/api/organization/batches/{batchId}/items/{fileId}/review")
public class ReviewController {
  public record Error(String code, String message) {}
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

  @ExceptionHandler(ReviewException.class)
  public ResponseEntity<Error> failure(ReviewException error) {
    return error(error.status(), error.code(), error.getMessage());
  }

  @ExceptionHandler(NamingException.class)
  public ResponseEntity<Error> namingFailure(NamingException error) {
    return error(error.status(), error.code(), error.getMessage());
  }

  private ResponseEntity<Error> error(int status, String code, String message) {
    return ResponseEntity.status(status).header("Cache-Control", "no-store").body(new Error(code, message));
  }
}
