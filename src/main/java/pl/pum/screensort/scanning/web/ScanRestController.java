package pl.pum.screensort.scanning.web;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.pum.screensort.scanning.image.ImageScanner;
import pl.pum.screensort.scanning.image.ScanResult;
import pl.pum.screensort.scanning.safety.InvalidScanFolderException;
import pl.pum.screensort.scanning.web.dto.ScanResultDto;

@RestController
public class ScanRestController {
  private final ImageScanner imageScanner;

  public ScanRestController(ImageScanner imageScanner) {
    this.imageScanner = imageScanner;
  }

  @PostMapping("/api/scan")
  public ScanResultDto scan(@RequestParam String folderPath) throws IOException {

    ScanResult result = imageScanner.scan(Path.of(folderPath));
    return ScanResultDto.from(result);
  }

  @ExceptionHandler(InvalidScanFolderException.class)
  public ResponseEntity<String> handleInvalidFolder(InvalidScanFolderException e) {
    return ResponseEntity.badRequest().body(e.getMessage()); // 400
  }
}
