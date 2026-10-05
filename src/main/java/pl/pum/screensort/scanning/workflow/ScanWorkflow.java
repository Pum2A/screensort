package pl.pum.screensort.scanning.workflow;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.stereotype.Service;
import pl.pum.screensort.catalog.reading.KnownFilesReader;
import pl.pum.screensort.catalog.writing.CatalogUpdater;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;
import pl.pum.screensort.scanning.image.ImageScanner;
import pl.pum.screensort.scanning.result.ScanResult;

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

  // PLAN DO WSPÓLNEGO UZUPEŁNIENIA: poniższe TODO nie zmieniają jeszcze działania.
  // Konstruktor przekazuje narzędzia; ta metoda dostaje folder konkretnego skanu.
  // WEJŚCIE: Path folder - ścieżka wybrana przez użytkownika, jeszcze niesprawdzona.
  // WYJŚCIE: ScanResult - wynik skanowania, po zapisaniu go w katalogu bazy danych.
  //
  // TODO 1: Przed skanowaniem wywołaj folderValidator.validate(folder).
  // Wynik zapisz jako AllowedRoot: zweryfikowaną ścieżkę, nie listę obrazów.
  // Jeśli walidacja rzuci wyjątek, nie odczytuj mapy i nie rozpoczynaj skanu.
  //
  // TODO 2: Przekaż ten AllowedRoot do knownFilesReader.read(...).
  // Wynik zapisz jako Map<String, KnownFile>: relativePath -> poprzedni opis pliku.
  // Mapa dotyczy jednego folderu; nowy folder daje pustą mapę, a nie błąd.
  // To dane z PostgreSQL-a, nie aktualna zawartość obrazów odczytana z dysku.
  //
  // TODO 3: Wspólnie zmień wejście ImageScanner, aby przyjmował AllowedRoot i mapę.
  // Obecne scan(Path) jeszcze tego nie obsługuje. Nie zmieniaj samego wywołania
  // na nowe argumenty, dopóki nie przygotujemy metody i jej testów w skanerze.
  // Docelowo walidacja jest tutaj; teraz nie usuwaj jej jeszcze z ImageScanner.
  // Porównanie aktualnych metadanych z mapą będzie osobnym krokiem w skanerze.
  //
  // TODO 4: Po zmianie wejścia skanera przekaż mu zweryfikowany folder oraz mapę.
  // Jego wynik nadal ma typ ScanResult i zostaje w zmiennej result poniżej.
  // Sama mapa nie zastępuje przeglądania folderu i nie przyspiesza jeszcze skanu.
  //
  // JUŻ JEST 5: CatalogUpdater zapisuje lub aktualizuje dane z result w bazie.
  // Walidator nie zapisuje wyniku. Nie otaczaj całego skanu transakcją bazy.
  // JUŻ JEST 6: Zwróć result do kontrolera, który przygotuje odpowiedź dla UI.
  //
  // PÓŹNIEJ: Rozdziel pomiary walidacji, odczytu mapy, skanowania i zapisu.
  // Obecne printy tylko mierzą czas; nie wykonują żadnego z tych działań.
  public ScanResult scanAndSave(Path folder) throws IOException {
    long start = System.nanoTime();
    ScanResult result = imageScanner.scan(folder);
    long afterScan = System.nanoTime();

    catalogUpdater.update(result.allowedRoot(), result);
    long afterSave = System.nanoTime();

    double scanMs = (afterScan - start) / 1_000_000.0;
    double databaseMs = (afterSave - afterScan) / 1_000_000.0;

    System.out.println("Skanowanie: " + scanMs + " ms");
    System.out.println("Baza danych: " + databaseMs + " ms");

    return result;
  }
}
