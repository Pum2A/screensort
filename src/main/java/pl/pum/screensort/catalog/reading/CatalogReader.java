package pl.pum.screensort.catalog.reading;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pum.screensort.catalog.database.AllowedRootEntity;
import pl.pum.screensort.catalog.database.AllowedRootRepository;
import pl.pum.screensort.catalog.database.FileRecordEntity;
import pl.pum.screensort.catalog.database.FileRecordRepository;
import pl.pum.screensort.catalog.dto.CatalogFileDto;
import pl.pum.screensort.catalog.dto.CatalogPageDto;
import pl.pum.screensort.scanning.folder.AllowedRoot;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;

@Service
public class CatalogReader {
  // Odczyt bez paginacji jest wygodny tylko dla małych folderów.
  static final int MAX_UNPAGINATED_FILES = 1000;

  private final ScanFolderValidator scanFolderValidator;
  private final AllowedRootRepository allowedRootRepository;
  private final FileRecordRepository fileRecordRepository;

  public CatalogReader(
      ScanFolderValidator scanFolderValidator,
      AllowedRootRepository allowedRootRepository,
      FileRecordRepository fileRecordRepository) {
    this.scanFolderValidator = scanFolderValidator;
    this.allowedRootRepository = allowedRootRepository;
    this.fileRecordRepository = fileRecordRepository;
  }

  @Transactional(readOnly = true)
  public CatalogPageDto readPage(String folderPath, int pageNumber, int pageSize)
      throws IOException {

    // Określamy, którą stronę i ile rekordów chcemy pobrać.
    PageRequest pageRequest = PageRequest.of(pageNumber, pageSize);

    // Sprawdzamy folder wskazany przez użytkownika.
    AllowedRoot allowedRoot = scanFolderValidator.validate(Path.of(folderPath));

    // Szukamy tego folderu w bazie.
    Optional<AllowedRootEntity> root =
        allowedRootRepository.findByCanonicalPath(allowedRoot.path().toString());

    // Folder istnieje na dysku, ale nie został jeszcze zapisany w bazie.
    if (root.isEmpty()) {
      return new CatalogPageDto(List.of(), pageNumber, pageSize, 0, false);
    }

    // Pobieramy z bazy tylko wybraną stronę rekordów.
    Page<FileRecordEntity> filePage =
        fileRecordRepository.findAllByAllowedRootOrderByRelativePathAsc(root.get(), pageRequest);

    // Zamieniamy encje tej strony na opisy przeznaczone dla UI.
    List<CatalogFileDto> files = new ArrayList<>();

    for (FileRecordEntity file : filePage.getContent()) {
      files.add(CatalogFileDto.from(file));
    }

    // Informacje o całym katalogu i kolejnej stronie.
    long totalFiles = filePage.getTotalElements();
    boolean hasNext = filePage.hasNext();

    return new CatalogPageDto(files, pageNumber, pageSize, totalFiles, hasNext);
  }

  @Transactional(readOnly = true)
  public List<CatalogFileDto> read(String folderPath) throws IOException {
    AllowedRoot allowedRoot = scanFolderValidator.validate(Path.of(folderPath));

    Optional<AllowedRootEntity> root =
        allowedRootRepository.findByCanonicalPath(allowedRoot.path().toString());

    if (root.isEmpty()) {
      return List.of();
    }

    // Odczyt bez paginacji służy tylko małym folderom. Duży katalog czytamy stronami.
    Page<FileRecordEntity> filePage =
        fileRecordRepository.findAllByAllowedRootOrderByRelativePathAsc(
            root.get(), PageRequest.of(0, MAX_UNPAGINATED_FILES + 1));
    if (filePage.getContent().size() > MAX_UNPAGINATED_FILES) {
      throw new CatalogTooLargeException(
          "Katalog ma więcej niż "
              + MAX_UNPAGINATED_FILES
              + " obrazów. Użyj endpointu /api/catalog/page.");
    }

    List<CatalogFileDto> result = new ArrayList<>();

    for (FileRecordEntity file : filePage.getContent()) {
      result.add(CatalogFileDto.from(file));
    }

    return result;
  }
}
