package pl.pum.screensort.gallery;

import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.pum.screensort.api.ApiException;

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
      throw new ApiException(400, "INVALID_PAGE", "Numer strony musi być >= 0, a rozmiar strony od 1 do 50.");
    }

    return catalogReader.readPage(folderPath, pageNumber, pageSize);
  }
}
