package pl.pum.screensort.gallery.reading;


/** Powiadomienie o etapie i licznikach. Nie uruchamia ani nie zapisuje skanu. */
@FunctionalInterface
public interface ScanProgressListener {
  ScanProgressListener NONE = (status, summary) -> {};

  void onProgress(ScanStatus status, ScanSummary summary);
}
