package pl.pum.screensort.catalog.reading;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.pum.screensort.catalog.database.AllowedRootEntity;
import pl.pum.screensort.catalog.database.AllowedRootRepository;
import pl.pum.screensort.catalog.database.FileRecordRepository;
import pl.pum.screensort.scanning.folder.AllowedRoot;
import pl.pum.screensort.scanning.knownfiles.KnownFile;

@Component
public class KnownFilesReader {
  // CEL: przygotować poprzednie opisy plików z bazy dla jednego skanowania.
  // Ta klasa nie otwiera zdjęć i nie podejmuje decyzji accepted/rejected.
  //
  private final AllowedRootRepository allowedRootRepository;
  private final FileRecordRepository fileRecordRepository;

  public KnownFilesReader(
      AllowedRootRepository allowedRootRepository, FileRecordRepository fileRecordRepository) {
    this.allowedRootRepository = allowedRootRepository;
    this.fileRecordRepository = fileRecordRepository;
  }

  @Transactional(readOnly = true)
  public Map<String, KnownFile> read(AllowedRoot allowedRoot) {
    String folderPath = allowedRoot.path().toString();
    Optional<AllowedRootEntity> existingRoot =
        allowedRootRepository.findByCanonicalPath(folderPath);

    List<KnownFile> knownFiles =
        existingRoot.isEmpty()
            ? List.of()
            : fileRecordRepository.findAllByAllowedRoot_Id(existingRoot.get().getId());

    Map<String, KnownFile> knowFilesByPath = new HashMap<>();

    for (KnownFile file : knownFiles) {
      knowFilesByPath.put(file.relativePath(), file);
    }
    return Map.copyOf(knowFilesByPath);
  }

  // WEJŚCIE: AllowedRoot - folder już sprawdzony przez ScanFolderValidator.
  // WYJŚCIE: Map<String, KnownFile> - ścieżka względna -> poprzedni opis pliku.
  // Przykład jednego wpisu: "foto.png" -> KnownFile z rozmiarem, datą i SHA-256.
  // KnownFile jest w scanning/knownfiles; to dane, a nie encja JPA ani zdjęcie.
  //
  // TODO 1: Przygotuj zależności AllowedRootRepository i FileRecordRepository.
  // Przekaż je konstruktorem. Klasa ma być komponentem Springa.
  // Metoda odczytu powinna działać w transakcji tylko do odczytu.
  //
  // TODO 2: Dodaj publiczną metodę przyjmującą AllowedRoot i zwracającą tę mapę.
  // Użyj allowedRoot.path(), aby znaleźć folder w bazie po canonicalPath.
  // Nie waliduj tutaj folderu ponownie i nie zapisuj nowego AllowedRootEntity.
  //
  // TODO 3: Jeśli folderu nie ma w bazie, zwróć pustą mapę - nie znamy jego plików.
  // Błąd połączenia lub zapytania nie oznacza "brak folderu"; nie ukrywaj go pustą mapą.
  //
  // TODO 4: Jeśli folder jest w bazie, pobierz jego pliki jednym zapytaniem repozytorium.
  // Zapytanie do dopisania ma zwracać List<KnownFile>, tylko cztery pola:
  // relativePath, sizeBytes, lastModifiedAt, sha256 - bez pełnych encji i bajtów zdjęć.
  // Folder ogranicza zapytanie; nie pobieraj plików ze wszystkich innych folderów.
  //
  // TODO 5: Zbuduj HashMap z pobranej listy: klucz = relativePath, wartość = KnownFile.
  // Każdy plik ma własny klucz. Ścieżka głównego folderu NIE jest kluczem tej mapy.
  // Zachowaj ten sam zapis relativePath co CatalogUpdater, aby klucze się zgadzały.
  //
  // TODO 6: Zwróć gotową mapę jako migawkę tylko do odczytu dla workerów.
  // Brak plików w znanym folderze również oznacza pustą mapę.
  //
  // PÓŹNIEJ: ScanWorkflow przekaże mapę do ImageScanner przed ciężkim odczytem plików.
  // To skaner porówna aktualny rozmiar i datę z KnownFile, nie ten reader.
  // Zgodność metadanych jest wskazówką, nie dowodem identycznej zawartości.
}
