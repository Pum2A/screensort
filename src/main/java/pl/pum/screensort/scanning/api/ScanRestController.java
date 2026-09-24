package pl.pum.screensort.scanning.api;

import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.pum.screensort.scanning.ScanWorkflow;
import pl.pum.screensort.scanning.image.ScanResult;
import pl.pum.screensort.scanning.safety.InvalidScanFolderException;
import pl.pum.screensort.scanning.api.dto.ScanRequest;
import pl.pum.screensort.scanning.api.dto.ScanResultDto;

@RestController
public class ScanRestController {
  private final ScanWorkflow scanWorkflow;

  public ScanRestController(ScanWorkflow scanWorkflow) {
    this.scanWorkflow = scanWorkflow;
  }

  @PostMapping("/api/scan")
  public ScanResultDto scan(@Valid @RequestBody ScanRequest request) throws IOException {

    ScanResult result = scanWorkflow.scanAndSave(Path.of(request.folderPath()));
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
