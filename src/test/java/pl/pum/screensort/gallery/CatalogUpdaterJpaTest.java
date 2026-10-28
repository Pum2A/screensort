package pl.pum.screensort.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import pl.pum.screensort.gallery.reading.AcceptedImage;
import pl.pum.screensort.gallery.reading.ScanResult;
import pl.pum.screensort.gallery.reading.ScanSummary;
import pl.pum.screensort.gallery.reading.AllowedRoot;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(CatalogUpdater.class)
class CatalogUpdaterJpaTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));

  @Autowired private CatalogUpdater catalogUpdater;
  @Autowired private AllowedRootRepository allowedRootRepository;
  @Autowired private FileRecordRepository fileRecordRepository;

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rescanningUpdatesOneFileInsteadOfCreatingDuplicates(@TempDir Path folder)
      throws InterruptedException {
    AllowedRoot allowedRoot = new AllowedRoot(folder);
    Path imagePath = folder.resolve("photo.png");
    Instant firstModifiedAt = Instant.parse("2026-09-20T08:30:00Z");
    Instant secondModifiedAt = Instant.parse("2026-09-21T09:45:00Z");

    AcceptedImage firstScanImage =
        new AcceptedImage(imagePath, "photo.png", "png", 2048L, firstModifiedAt, "a".repeat(64));
    catalogUpdater.update(
        allowedRoot,
        new ScanResult(allowedRoot, List.of(firstScanImage), List.of(), new ScanSummary(1, 0, 0, 0)));

    AllowedRootEntity savedRoot =
        allowedRootRepository.findByCanonicalPath(folder.toString()).orElseThrow();
    FileRecordEntity firstRecord =
        fileRecordRepository.findByAllowedRootAndRelativePath(savedRoot, "photo.png").orElseThrow();
    Long firstId = firstRecord.getId();
    Instant firstDiscoveredAt = firstRecord.getFirstDiscoveredAt();
    Instant firstSeenAt = firstRecord.getLastSeenAt();

    Thread.sleep(20);

    AcceptedImage secondScanImage =
        new AcceptedImage(imagePath, "photo.png", "png", 4096L, secondModifiedAt, "b".repeat(64));
    catalogUpdater.update(
        allowedRoot,
        new ScanResult(allowedRoot, List.of(secondScanImage), List.of(), new ScanSummary(0, 1, 0, 0)));

    AllowedRootEntity sameRoot =
        allowedRootRepository.findByCanonicalPath(folder.toString()).orElseThrow();
    FileRecordEntity updatedRecord =
        fileRecordRepository.findByAllowedRootAndRelativePath(sameRoot, "photo.png").orElseThrow();

    assertThat(allowedRootRepository.count()).isEqualTo(1);
    assertThat(fileRecordRepository.count()).isEqualTo(1);
    assertThat(sameRoot.getId()).isEqualTo(savedRoot.getId());
    assertThat(updatedRecord.getId()).isEqualTo(firstId);
    assertThat(updatedRecord.getFirstDiscoveredAt()).isEqualTo(firstDiscoveredAt);
    assertThat(updatedRecord.getLastSeenAt()).isAfter(firstSeenAt);
    assertThat(updatedRecord.getSizeBytes()).isEqualTo(4096L);
    assertThat(updatedRecord.getLastModifiedAt()).isEqualTo(secondModifiedAt);
    assertThat(updatedRecord.getSha256()).isEqualTo("b".repeat(64));
  }
}
