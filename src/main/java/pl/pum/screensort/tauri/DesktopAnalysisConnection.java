package pl.pum.screensort.tauri;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.pum.screensort.analysis.engine.LlamaAnalysisClient;
import pl.pum.screensort.analysis.engine.PythonAnalysisClient;

/** The session filter authenticates these routes; the native bridge does not expose them. */
@RestController
@Profile("desktop")
public class DesktopAnalysisConnection {
  private final PythonAnalysisClient python;
  private final LlamaAnalysisClient llama;

  public DesktopAnalysisConnection(PythonAnalysisClient python, LlamaAnalysisClient llama) {
    this.python = python;
    this.llama = llama;
  }

  public record Connection(String url, String token) {}

  /** {@code model} and {@code device} are labels stored with each result, e.g. qwen3.5-4b and gpu. */
  public record VisionConnection(String url, String token, String model, String device) {}

  @PostMapping("/_internal/analysis-connection")
  public ResponseEntity<Void> connect(@RequestBody Connection connection) {
    try {
      python.connect(connection.url(), connection.token());
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException | NullPointerException error) {
      return ResponseEntity.badRequest().build();
    }
  }

  @PostMapping("/_internal/vision-connection")
  public ResponseEntity<Void> connectVision(@RequestBody VisionConnection connection) {
    try {
      // The launcher always protects llama-server with a key; only a developer's server runs without.
      if (connection.token().isBlank()) return ResponseEntity.badRequest().build();
      llama.connect(connection.url(), connection.token(), connection.model(), connection.device());
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException | NullPointerException error) {
      return ResponseEntity.badRequest().build();
    }
  }
}
