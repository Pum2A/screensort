package pl.pum.screensort.tauri;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.pum.screensort.analysis.engine.PythonAnalysisClient;

/** The session filter authenticates this route; the native bridge does not expose it. */
@RestController
@Profile("desktop")
public class DesktopAnalysisConnection {
  private final PythonAnalysisClient python;

  public DesktopAnalysisConnection(PythonAnalysisClient python) { this.python = python; }

  public record Connection(String url, String token) {}

  @PostMapping("/_internal/analysis-connection")
  public ResponseEntity<Void> connect(@RequestBody Connection connection) {
    try {
      python.connect(connection.url(), connection.token());
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException | NullPointerException error) {
      return ResponseEntity.badRequest().build();
    }
  }
}
