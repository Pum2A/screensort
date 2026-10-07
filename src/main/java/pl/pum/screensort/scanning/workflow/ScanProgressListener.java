package pl.pum.screensort.scanning.workflow;

import pl.pum.screensort.scanning.jobs.ScanStatus;
import pl.pum.screensort.scanning.result.ScanSummary;

/** Powiadomienie o etapie i licznikach. Nie uruchamia ani nie zapisuje skanu. */
@FunctionalInterface
public interface ScanProgressListener {
  ScanProgressListener NONE = (status, summary) -> {};

  void onProgress(ScanStatus status, ScanSummary summary);
}
