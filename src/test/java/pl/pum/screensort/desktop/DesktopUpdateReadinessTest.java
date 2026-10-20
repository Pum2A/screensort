package pl.pum.screensort.desktop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import pl.pum.screensort.analysis.jobs.AnalysisJobManager;
import pl.pum.screensort.organization.BatchManager;
import pl.pum.screensort.scanning.jobs.ScanJobManager;

class DesktopUpdateReadinessTest {
  @Test
  void onlyIdleBackendMayBeStoppedForInstallation() {
    var scans = mock(ScanJobManager.class);
    var analyses = mock(AnalysisJobManager.class);
    var batches = mock(BatchManager.class);
    var lifecycle = new DesktopLifecycle(mock(ConfigurableApplicationContext.class), "unused-state.json", scans, analyses, batches);
    assertTrue(lifecycle.updateReady().get("ready"));
    when(scans.hasActiveJob()).thenReturn(true);
    assertFalse(lifecycle.updateReady().get("ready"));
    when(scans.hasActiveJob()).thenReturn(false);
    when(analyses.hasActiveJob()).thenReturn(true);
    assertFalse(lifecycle.updateReady().get("ready"));
    when(analyses.hasActiveJob()).thenReturn(false);
    when(batches.hasActiveJob()).thenReturn(true);
    assertFalse(lifecycle.updateReady().get("ready"));
  }
}
