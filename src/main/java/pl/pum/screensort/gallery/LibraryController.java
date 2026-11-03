package pl.pum.screensort.gallery;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.pum.screensort.gallery.reading.InvalidScanFolderException;

@RestController
public class LibraryController {
  private static final Logger log = LoggerFactory.getLogger(LibraryController.class);

  private final LibraryReader library;
  private final TextSearch search;

  public LibraryController(LibraryReader library, TextSearch search) {
    this.library = library; this.search = search;
  }

  public record SearchResult(java.util.List<TextSearch.Hit> hits) {}

  @GetMapping("/api/library")
  public ResponseEntity<LibraryReader.Library> read(@RequestParam String folderPath) throws IOException {
    // Decisions change between two reads, so the library is never served from a cache.
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(library.read(folderPath));
  }

  /** Images whose text or AI description contains {@code q}; names are filtered in the app itself. */
  @GetMapping("/api/library/search")
  public ResponseEntity<SearchResult> search(@RequestParam String folderPath, @RequestParam String q) throws IOException {
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(new SearchResult(search.search(folderPath, q)));
  }

  @ExceptionHandler(InvalidScanFolderException.class)
  public ResponseEntity<String> handleInvalidFolder(InvalidScanFolderException e) {
    return ResponseEntity.badRequest().body(e.getMessage());
  }

  @ExceptionHandler(InvalidPathException.class)
  public ResponseEntity<String> handleInvalidPath(InvalidPathException e) {
    return ResponseEntity.badRequest().body("Nieprawidłowa ścieżka folderu.");
  }

  @ExceptionHandler(CatalogTooLargeException.class)
  public ResponseEntity<String> handleTooLargeCatalog(CatalogTooLargeException e) {
    return ResponseEntity.badRequest().body("Biblioteka pokazuje foldery do 1000 obrazów.");
  }

  // The IOException message holds the folder path, so it never reaches the response or the log.
  @ExceptionHandler(IOException.class)
  public ResponseEntity<String> handleIoFailure(IOException e) {
    log.warn("Library folder read failed: {}", e.getClass().getSimpleName());
    return ResponseEntity.internalServerError().body("Nie można odczytać wybranego folderu.");
  }
}
