package pl.pum.screensort.gallery;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pl.pum.screensort.errors.ApiError;
import pl.pum.screensort.gallery.preview.CatalogImagePreview;
import pl.pum.screensort.gallery.preview.ImagePreviewException;

@RestController
public class CatalogImageController {
  private final CatalogImagePreview imagePreview;

  public CatalogImageController(CatalogImagePreview imagePreview) {
    this.imagePreview = imagePreview;
  }

  @GetMapping("/api/catalog/files/{id}/thumbnail")
  public ResponseEntity<byte[]> thumbnail(@PathVariable long id) {
    return image(id, CatalogImagePreview.Size.THUMBNAIL);
  }

  @GetMapping("/api/catalog/files/{id}/preview")
  public ResponseEntity<byte[]> preview(@PathVariable long id) {
    return image(id, CatalogImagePreview.Size.PREVIEW);
  }

  private ResponseEntity<byte[]> image(long id, CatalogImagePreview.Size size) {
    byte[] png = imagePreview.read(id, size);
    return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
        .contentLength(png.length).body(png);
  }

  // Stays here, not in ApiExceptionHandler: image answers also need nosniff and, when busy, Retry-After.
  @ExceptionHandler(ImagePreviewException.class)
  public ResponseEntity<ApiError> unavailable(ImagePreviewException exception) {
    HttpStatus status = switch (exception.reason()) {
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case UNSAFE_PATH -> HttpStatus.FORBIDDEN;
      case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
      case INVALID_IMAGE -> HttpStatus.UNPROCESSABLE_CONTENT;
      case CHANGED -> HttpStatus.CONFLICT;
      case BUSY -> HttpStatus.SERVICE_UNAVAILABLE;
    };
    var response = ResponseEntity.status(status).cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Content-Type-Options", "nosniff");
    if (status == HttpStatus.SERVICE_UNAVAILABLE) response.header("Retry-After", "1");
    return response.body(new ApiError("IMAGE_" + exception.reason().name(), exception.getMessage()));
  }
}
