package pl.pum.screensort.desktop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import pl.pum.screensort.analysis.AnalysisJobManager;
import pl.pum.screensort.analysis.BatchManager;
import pl.pum.screensort.export.ExportManager;
import pl.pum.screensort.gallery.reading.ScanJobManager;

class DesktopUpdateReadinessTest {
  @Test
  void onlyIdleBackendMayBeStoppedForInstallation() {
    var scans = mock(ScanJobManager.class);
    var analyses = mock(AnalysisJobManager.class);
    var batches = mock(BatchManager.class);
    var exports = mock(ExportManager.class);
    var lifecycle = new DesktopLifecycle(mock(ConfigurableApplicationContext.class), "unused-state.json", scans, analyses, batches, exports);
    assertTrue(lifecycle.updateReady().get("ready"));
    when(scans.hasActiveJob()).thenReturn(true);
    assertFalse(lifecycle.updateReady().get("ready"));
    when(scans.hasActiveJob()).thenReturn(false);
    when(analyses.hasActiveJob()).thenReturn(true);
    assertFalse(lifecycle.updateReady().get("ready"));
    when(analyses.hasActiveJob()).thenReturn(false);
    when(batches.hasActiveJob()).thenReturn(true);
    assertFalse(lifecycle.updateReady().get("ready"));
    when(batches.hasActiveJob()).thenReturn(false);
    when(exports.hasActiveJob()).thenReturn(true);
    assertFalse(lifecycle.updateReady().get("ready"));
  }
}
