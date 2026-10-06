package pl.pum.screensort.catalog.preview;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pum.screensort.catalog.database.FileRecordEntity;
import pl.pum.screensort.catalog.database.FileRecordRepository;

@Service
public class CatalogImageSourceReader {
  private final FileRecordRepository fileRecordRepository;

  public CatalogImageSourceReader(FileRecordRepository fileRecordRepository) {
    this.fileRecordRepository = fileRecordRepository;
  }

  @Transactional(readOnly = true)
  public ImageSource read(long fileId) {
    FileRecordEntity file = fileRecordRepository.findById(fileId)
        .orElseThrow(() -> new ImagePreviewException(
            ImagePreviewException.Reason.NOT_FOUND, "Nie znaleziono obrazu w katalogu."));

    // Relację JPA odczytujemy w transakcji. Dekodowanie obrazu odbędzie się już poza nią.
    return new ImageSource(fileId, file.getAllowedRoot().getCanonicalPath(),
        file.getRelativePath(), file.getSha256());
  }

  public record ImageSource(long fileId, String folderPath, String relativePath, String sha256) {}
}
