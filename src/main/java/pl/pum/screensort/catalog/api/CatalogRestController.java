package pl.pum.screensort.catalog.api;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.pum.screensort.catalog.dto.CatalogFileDto;
import pl.pum.screensort.catalog.dto.CatalogPageDto;
import pl.pum.screensort.catalog.reading.CatalogReader;
import pl.pum.screensort.catalog.reading.CatalogTooLargeException;
import pl.pum.screensort.scanning.folder.InvalidScanFolderException;

@RestController
public class CatalogRestController {
  private final CatalogReader catalogReader;

  public CatalogRestController(CatalogReader catalogReader) {
    this.catalogReader = catalogReader;
  }

  @GetMapping("/api/catalog/files")
  public List<CatalogFileDto> files(@RequestParam String folderPath) throws IOException {
    return catalogReader.read(folderPath);
  }

  @GetMapping("/api/catalog/page")
  public CatalogPageDto page(
      @RequestParam String folderPath,
      @RequestParam(defaultValue = "0") int pageNumber,
      @RequestParam(defaultValue = "50") int pageSize)
      throws IOException {

    if (pageNumber < 0 || pageSize < 1 || pageSize > 50) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Numer strony musi być >= 0, a rozmiar strony od 1 do 50.");
    }

    return catalogReader.readPage(folderPath, pageNumber, pageSize);
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
    return ResponseEntity.badRequest().body(e.getMessage());
  }
}
