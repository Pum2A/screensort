package pl.pum.screensort.gallery;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import pl.pum.screensort.gallery.reading.AllowedRoot;
import pl.pum.screensort.gallery.reading.AcceptedImage;
import pl.pum.screensort.gallery.reading.ScanResult;

@Service
public class CatalogUpdater {

  private final AllowedRootRepository allowedRootRepository;
  private final FileRecordRepository fileRecordRepository;

  public CatalogUpdater(
      AllowedRootRepository allowedRootRepository, FileRecordRepository fileRecordRepository) {
    this.allowedRootRepository = allowedRootRepository;
    this.fileRecordRepository = fileRecordRepository;
  }

  @Transactional
  public void update(AllowedRoot allowedRoot, ScanResult scanResult) {
    String folderPath = allowedRoot.path().toString();
    Optional<AllowedRootEntity> existingRoot =
        allowedRootRepository.findByCanonicalPath(folderPath);
    AllowedRootEntity rootEntity =
        existingRoot.orElseGet(
            () ->
                allowedRootRepository.save(
                    new AllowedRootEntity(folderPath, false, Instant.now())));
    for (AcceptedImage image : scanResult.accepted()) {
      String relativePath = allowedRoot.path().relativize(image.path()).toString();

      Optional<FileRecordEntity> existingFile =
          fileRecordRepository.findByAllowedRootAndRelativePath(rootEntity, relativePath);

      if (existingFile.isEmpty()) {
        FileRecordEntity newFile =
            new FileRecordEntity(
                relativePath,
                image.originalName(),
                image.extension(),
                image.sizeBytes(),
                image.hash(),
                rootEntity,
                image.lastModifiedAt().truncatedTo(ChronoUnit.MICROS),
                Instant.now());

        fileRecordRepository.save(newFile);
      } else {
        FileRecordEntity storedFile = existingFile.get();
        storedFile.updateFromScan(
            image.originalName(),
            image.extension(),
            image.sizeBytes(),
            image.hash(),
            image.lastModifiedAt().truncatedTo(ChronoUnit.MICROS),
            Instant.now());
        fileRecordRepository.save(storedFile);
      }
    }
  }
}
