package pl.pum.screensort.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.pum.screensort.analysis.jobs.AnalysisJobManager;
import pl.pum.screensort.organization.BatchManager;
import pl.pum.screensort.organization.export.ExportManager;
import pl.pum.screensort.gallery.reading.ScanJobManager;

@Profile("desktop")
@RestController
public class DesktopLifecycle {
  private final ConfigurableApplicationContext context;
  private final Path stateFile;
  private final ScanJobManager scans;
  private final AnalysisJobManager analyses;
  private final BatchManager batches;
  private final ExportManager exports;

  public DesktopLifecycle(ConfigurableApplicationContext context,
      @Value("${screensort.desktop.state-file}") String stateFile,
      ScanJobManager scans, AnalysisJobManager analyses, BatchManager batches, ExportManager exports) {
    this.context = context;
    this.stateFile = Path.of(stateFile);
    this.scans = scans;
    this.analyses = analyses;
    this.batches = batches;
    this.exports = exports;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void ready(ApplicationReadyEvent event) throws IOException {
    int port = ((WebServerApplicationContext) context).getWebServer().getPort();
    String state = "{\"port\":" + port + ",\"pid\":" + ProcessHandle.current().pid() + "}";
    Files.writeString(stateFile, state, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
  }

  @GetMapping("/_internal/ready")
  public Map<String, String> ready() { return Map.of("status", "ready"); }

  // Native bridge closes admission before this check; never exposed to the WebView.
  @GetMapping("/_internal/update-ready")
  public Map<String, Boolean> updateReady() {
    return Map.of("ready", !scans.hasActiveJob() && !analyses.hasActiveJob() && !batches.hasActiveJob() && !exports.hasActiveJob());
  }

  @PostMapping("/_internal/shutdown")
  public Map<String, String> shutdown() {
    Thread.ofPlatform().name("desktop-shutdown").start(() -> {
      try { Thread.sleep(150); }
      catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      context.close();
    });
    return Map.of("status", "stopping");
  }
}
