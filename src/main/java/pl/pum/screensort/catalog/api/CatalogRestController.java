package pl.pum.screensort.catalog.api;

import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.pum.screensort.catalog.dto.CatalogFileDto;
import pl.pum.screensort.catalog.reading.CatalogReader;
import pl.pum.screensort.scanning.safety.InvalidScanFolderException;

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

  @ExceptionHandler(InvalidScanFolderException.class)
  public ResponseEntity<String> handleInvalidFolder(InvalidScanFolderException e) {
    return ResponseEntity.badRequest().body(e.getMessage());
  }
}
