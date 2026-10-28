package pl.pum.screensort.gallery.reading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScanJobTest {
  @Test
  void keepsAnImmutableSnapshotOfOneRun() {
    UUID id = UUID.randomUUID();
    ScanSummary summary = new ScanSummary(1, 2, 3, 4);
    ScanJob job = new ScanJob(id, "photos", ScanStatus.SCANNING, summary, null);
    assertEquals(id, job.id());
    assertEquals(summary, job.summary());
    assertEquals("photos", job.folderPath());
  }

  @Test
  void requiresAnErrorMessageOnlyForFailedJobs() {
    ScanSummary summary = new ScanSummary(0, 0, 0, 0);
    assertThrows(
        IllegalArgumentException.class,
        () -> new ScanJob(UUID.randomUUID(), "photos", ScanStatus.FAILED, summary, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ScanJob(UUID.randomUUID(), "photos", ScanStatus.FAILED, summary, " "));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ScanJob(UUID.randomUUID(), "photos", ScanStatus.COMPLETED, summary, "error"));
  }

  @Test
  void rejectsMissingRequiredData() {
    ScanSummary summary = new ScanSummary(0, 0, 0, 0);
    assertThrows(
        NullPointerException.class,
        () -> new ScanJob(null, "photos", ScanStatus.PREPARING, summary, null));
    assertThrows(
        NullPointerException.class,
        () -> new ScanJob(UUID.randomUUID(), "photos", null, summary, null));
    assertThrows(
        NullPointerException.class,
        () -> new ScanJob(UUID.randomUUID(), "photos", ScanStatus.PREPARING, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ScanJob(UUID.randomUUID(), " ", ScanStatus.PREPARING, summary, null));
  }
}
