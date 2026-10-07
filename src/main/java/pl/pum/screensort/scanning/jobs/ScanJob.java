package pl.pum.screensort.scanning.jobs;

import java.util.Objects;
import java.util.UUID;
import pl.pum.screensort.scanning.result.ScanSummary;

/** Niemodyfikowalny opis aktualnego stanu jednego uruchomienia skanu. */
public record ScanJob(
    UUID id, String folderPath, ScanStatus status, ScanSummary summary, String errorMessage) {
  public ScanJob {
    Objects.requireNonNull(id);
    Objects.requireNonNull(folderPath);
    Objects.requireNonNull(status);
    Objects.requireNonNull(summary);
    if (folderPath.isBlank()) {
      throw new IllegalArgumentException("Ścieżka zadania nie może być pusta.");
    }
    if (status == ScanStatus.FAILED && (errorMessage == null || errorMessage.isBlank())) {
      throw new IllegalArgumentException("Nieudane zadanie musi mieć komunikat błędu.");
    }
    if (status != ScanStatus.FAILED && errorMessage != null) {
      throw new IllegalArgumentException("Komunikat błędu dotyczy tylko nieudanego zadania.");
    }
  }
}
