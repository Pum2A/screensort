package pl.pum.screensort.catalog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pum.screensort.catalog.database.AllowedRootEntity;
import pl.pum.screensort.catalog.database.AllowedRootRepository;
import pl.pum.screensort.catalog.database.FileRecordEntity;
import pl.pum.screensort.catalog.database.FileRecordRepository;
import pl.pum.screensort.catalog.dto.CatalogFileDto;
import pl.pum.screensort.scanning.safety.AllowedRoot;
import pl.pum.screensort.scanning.safety.ScanFolderValidator;

@Service
public class CatalogReader {
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
  public List<CatalogFileDto> read(String folderPath) throws IOException {
    AllowedRoot allowedRoot = scanFolderValidator.validate(Path.of(folderPath));

    Optional<AllowedRootEntity> root =
        allowedRootRepository.findByCanonicalPath(allowedRoot.path().toString());

    if (root.isEmpty()) {
      return List.of();
    }

    List<CatalogFileDto> result = new ArrayList<>();

    for (FileRecordEntity file :
        fileRecordRepository.findAllByAllowedRootOrderByRelativePathAsc(root.get())) {
      result.add(CatalogFileDto.from(file));
    }

    return result;
  }
}
