package pl.pum.screensort.scanning.web;

import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.pum.screensort.scanning.image.ImageScanner;
import pl.pum.screensort.scanning.image.ScanResult;
import pl.pum.screensort.scanning.safety.InvalidScanFolderException;
import pl.pum.screensort.scanning.web.dto.ScanRequest;
import pl.pum.screensort.scanning.web.dto.ScanResultDto;

@RestController
public class ScanRestController {
  private final ImageScanner imageScanner;

  public ScanRestController(ImageScanner imageScanner) {
    this.imageScanner = imageScanner;
  }

  @PostMapping("/api/scan")
  public ScanResultDto scan(@Valid @RequestBody ScanRequest request) throws IOException {

    ScanResult result = imageScanner.scan(Path.of(request.folderPath()));
    return ScanResultDto.from(result);
  }

  @ExceptionHandler(InvalidScanFolderException.class)
  public ResponseEntity<String> handleInvalidFolder(InvalidScanFolderException e) {
    return ResponseEntity.badRequest().body(e.getMessage()); // 400
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<String> handleValidation(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest()
        .body(e.getBindingResult().getFieldError().getDefaultMessage());
  }
}
