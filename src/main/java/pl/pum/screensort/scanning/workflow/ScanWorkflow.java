package pl.pum.screensort.scanning.workflow;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Service;
import pl.pum.screensort.catalog.reading.KnownFilesReader;
import pl.pum.screensort.catalog.writing.CatalogUpdater;
import pl.pum.screensort.scanning.folder.AllowedRoot;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;
import pl.pum.screensort.scanning.image.ImageScanner;
import pl.pum.screensort.scanning.jobs.ScanStatus;
import pl.pum.screensort.scanning.knownfiles.KnownFile;
import pl.pum.screensort.scanning.result.ScanResult;
import pl.pum.screensort.scanning.result.ScanSummary;

@Service
public class ScanWorkflow {
  private final ImageScanner imageScanner;
  private final CatalogUpdater catalogUpdater;
  private final ScanFolderValidator folderValidator;
  private final KnownFilesReader knownFilesReader;

  public ScanWorkflow(
      ImageScanner imageScanner,
      CatalogUpdater catalogUpdater,
      ScanFolderValidator folderValidator,
      KnownFilesReader knownFilesReader) {
    this.imageScanner = imageScanner;
    this.catalogUpdater = catalogUpdater;
    this.folderValidator = folderValidator;
    this.knownFilesReader = knownFilesReader;
  }

  // STAN I PLAN: walidacja, mapa znanych plików, skan przyrostowy i zapis są podłączone.
  // Konstruktor przekazuje narzędzia; ta metoda dostaje folder konkretnego skanu.
  // WEJŚCIE: Path folder - ścieżka wybrana przez użytkownika, jeszcze niesprawdzona.
  // WYJŚCIE: ScanResult - wynik skanowania, po zapisaniu go w katalogu bazy danych.
  //
  // GOTOWE 1: Przed skanowaniem wywołujemy folderValidator.validate(folder).
  // Wynik zapisz jako AllowedRoot: zweryfikowaną ścieżkę, nie listę obrazów.
  // Jeśli walidacja rzuci wyjątek, nie odczytuj mapy i nie rozpoczynaj skanu.
  //
  // GOTOWE 2: Przekazujemy ten AllowedRoot do knownFilesReader.read(...).
  // Wynik zapisz jako Map<String, KnownFile>: relativePath -> poprzedni opis pliku.
  // Mapa dotyczy jednego folderu; nowy folder daje pustą mapę, a nie błąd.
  // To dane z PostgreSQL-a, nie aktualna zawartość obrazów odczytana z dysku.
  //
  // GOTOWE 3: ImageScanner przyjmuje AllowedRoot i mapę; nie waliduje folderu ponownie.
  // Walidator jest zależnością workflow, a nie konstruktora skanera.
  //
  // GOTOWE 4: Przekazujemy skanerowi zweryfikowany folder oraz mapę.
  // Jego wynik nadal ma typ ScanResult i zostaje w zmiennej result poniżej.
  // Mapa nie zastępuje przeglądania folderu, ale pozwala pominąć ciężki odczyt znanych plików.
  //
  // JUŻ JEST 5: CatalogUpdater zapisuje lub aktualizuje dane z result w bazie.
  // Walidator nie zapisuje wyniku. Nie otaczaj całego skanu transakcją bazy.
  // JUŻ JEST 6: Zwróć result do kontrolera, który przygotuje odpowiedź dla UI.
  //
  // GOTOWE 7: ImageScanner przekazuje folder i mapę do processFile dla jednego pliku.
  // GOTOWE 8: processFile oblicza ścieżkę względną pliku i wyszukuje ją w mapie.
  // Wynik to poprzedni opis KnownFile albo brak wpisu, a nie decyzja o akceptacji.
  // GOTOWE 9: Po tanich kontrolach porównujemy rozmiar i datę modyfikacji.
  // Zgodne metadane pozwalają użyć zapisanego SHA-256 bez dekodowania i hashowania.
  // ScanSummary rozróżnia nowe, sprawdzone ponownie, znane bez zmian i odrzucone.
  // PÓŹNIEJ: Dodaj osobne pomiary walidacji, odczytu mapy, skanowania i zapisu.
  // Właściciel usunął wcześniejsze zbiorcze printy czasu z tego workflow.
  public ScanResult scanAndSave(Path folder) throws IOException {
    return scanAndSave(folder, ScanProgressListener.NONE);
  }

  public ScanResult scanAndSave(Path folder, ScanProgressListener progress) throws IOException {
    ScanSummary emptySummary = new ScanSummary(0, 0, 0, 0);
    progress.onProgress(ScanStatus.PREPARING, emptySummary);
    AllowedRoot allowedRoot = folderValidator.validate(folder);
    Map<String, KnownFile> knowFilesByPath = knownFilesReader.read(allowedRoot);
    progress.onProgress(ScanStatus.SCANNING, emptySummary);
    ScanResult result =
        imageScanner.scan(
            allowedRoot,
            knowFilesByPath,
            summary -> progress.onProgress(ScanStatus.SCANNING, summary));
    progress.onProgress(ScanStatus.SAVING, result.summary());
    catalogUpdater.update(result.allowedRoot(), result);
    return result;
  }
}
