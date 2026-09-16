package pl.pum.screensort.scanning.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.pum.screensort.scanning.image.ImageScanner;
import pl.pum.screensort.scanning.image.ScanResult;

import java.io.IOException;
import java.nio.file.Path;

@RestController
public class ScanRestController {
  private final ImageScanner imageScanner;

  public ScanRestController(ImageScanner imageScanner) {
    this.imageScanner = imageScanner;
  }

  @PostMapping("/api/scan")
  public ScanResult scan(@RequestParam String folderPath) throws IOException {

    Path path = Path.of(folderPath);

    return imageScanner.scan(path);
  }
}
