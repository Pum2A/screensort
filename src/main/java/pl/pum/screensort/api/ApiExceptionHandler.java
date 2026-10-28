package pl.pum.screensort.api;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.gallery.CatalogTooLargeException;
import pl.pum.screensort.naming.NamingException;
import pl.pum.screensort.export.ExportException;
import pl.pum.screensort.review.ReviewException;
import pl.pum.screensort.gallery.reading.InvalidScanFolderException;
import pl.pum.screensort.gallery.reading.ScanAlreadyRunningException;

/** Every controller answers errors the same way: a status, {@link ApiError} JSON and no caching. */
@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  public static ResponseEntity<ApiError> error(int status, String code, String message) {
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new ApiError(code, message));
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> rejected(ApiException error) {
    return error(error.status(), error.code(), error.getMessage());
  }

  @ExceptionHandler(NamingException.class)
  public ResponseEntity<ApiError> naming(NamingException error) {
    return error(error.status(), error.code(), error.getMessage());
  }

  @ExceptionHandler(ReviewException.class)
  public ResponseEntity<ApiError> review(ReviewException error) {
    return error(error.status(), error.code(), error.getMessage());
  }

  @ExceptionHandler(ExportException.class)
  public ResponseEntity<ApiError> export(ExportException error) {
    return error(error.status(), error.code(), error.getMessage());
  }

  // AnalysisException messages are written for the user; the status decides the code.
  @ExceptionHandler(AnalysisException.class)
  public ResponseEntity<ApiError> analysis(AnalysisException error) {
    return error(error.status(), analysisCode(error.status()), error.getMessage());
  }

  static String analysisCode(int status) {
    return switch (status) {
      case 400 -> "INVALID_REQUEST";
      case 404 -> "NOT_FOUND";
      case 409 -> "CONFLICT";
      case 413 -> "TOO_LARGE";
      case 422 -> "INVALID_IMAGE";
      case 502 -> "INVALID_RESULT";
      case 503 -> "ANALYSIS_UNAVAILABLE";
      case 504 -> "ANALYSIS_TIMEOUT";
      default -> "ANALYSIS_FAILED";
    };
  }

  @ExceptionHandler(ScanAlreadyRunningException.class)
  public ResponseEntity<ApiError> scanRunning(ScanAlreadyRunningException error) {
    return error(409, "SCAN_RUNNING", error.getMessage());
  }

  @ExceptionHandler(TaskRejectedException.class)
  public ResponseEntity<ApiError> busy(TaskRejectedException error) {
    // The executor's own message describes its internals, so it is never forwarded.
    return error(503, "BUSY", "Nie można teraz uruchomić skanu. Spróbuj ponownie później.");
  }

  @ExceptionHandler(InvalidScanFolderException.class)
  public ResponseEntity<ApiError> invalidFolder(InvalidScanFolderException error) {
    return error(400, "INVALID_FOLDER", error.getMessage());
  }

  @ExceptionHandler(InvalidPathException.class)
  public ResponseEntity<ApiError> invalidPath(InvalidPathException error) {
    // InvalidPathException repeats the path in its message, so a fixed sentence replaces it.
    return error(400, "INVALID_PATH", "Ścieżka ma niepoprawny format.");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException error) {
    var first = error.getBindingResult().getAllErrors().stream().findFirst();
    return error(400, "INVALID_REQUEST",
        first.map(item -> item.getDefaultMessage()).orElse("Nieprawidłowe dane żądania."));
  }

  @ExceptionHandler(CatalogTooLargeException.class)
  public ResponseEntity<ApiError> catalogTooLarge(CatalogTooLargeException error) {
    return error(400, "CATALOG_TOO_LARGE", error.getMessage());
  }

  @ExceptionHandler(IOException.class)
  public ResponseEntity<ApiError> unreadable(IOException error) {
    log.warn("Folder read failed: {}", error.getClass().getSimpleName());
    return error(500, "FOLDER_UNREADABLE", "Nie można odczytać wybranego folderu.");
  }
}
