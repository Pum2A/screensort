package pl.pum.screensort.scanning.jobs;

/** Etap całego zadania, nie wynik sprawdzania pojedynczego obrazu. */
public enum ScanStatus {
  PREPARING,
  SCANNING,
  SAVING,
  COMPLETED,
  FAILED
}
