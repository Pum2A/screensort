package pl.pum.screensort.catalog.database;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CatalogEntityJpaTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));

  @Autowired private EntityManager entityManager;
  @Autowired private AllowedRootRepository allowedRootRepository;
  @Autowired private FileRecordRepository fileRecordRepository;

  private static void assertField(String field, Object expected, Object actual) {
    assertThat(actual).as(field).isEqualTo(expected);
  }

  @Test
  void savesAndFindsAllowedRootByCanonicalPath() {
    Instant now = Instant.parse("2026-09-22T12:00:00Z");
    String canonicalPath = "C:\\Test\\Pictures";

    AllowedRootEntity allowedRoot = new AllowedRootEntity(canonicalPath, false, now);

    AllowedRootEntity saved = allowedRootRepository.save(allowedRoot);

    entityManager.flush();

    Long savedId = saved.getId();

    entityManager.clear();

    Optional<AllowedRootEntity> found = allowedRootRepository.findByCanonicalPath(canonicalPath);

    assertThat(found).isPresent();

    AllowedRootEntity loaded = found.orElseThrow();

    assertField("id", savedId, loaded.getId());
    assertField("canonicalPath", canonicalPath, loaded.getCanonicalPath());
    assertField("recursiveScan", false, loaded.isRecursiveScan());
    assertField("createdAt", now, loaded.getCreatedAt());
    assertField("updatedAt", now, loaded.getUpdatedAt());

    assertThat(loaded.getId()).isEqualTo(savedId);
    assertThat(loaded.getCanonicalPath()).isEqualTo(canonicalPath);
    assertThat(loaded.isRecursiveScan()).isFalse();
    assertThat(loaded.getCreatedAt()).isEqualTo(now);
    assertThat(loaded.getUpdatedAt()).isEqualTo(now);
  }

  @Test
  void savesAndFindsFileRecordByAllowedRootAndRelativePath() {
    Instant scanTime = Instant.parse("2026-09-22T12:00:00Z");
    Instant lastModifiedAt = Instant.parse("2026-09-20T08:30:00Z");
    String canonicalPath = "C:\\Test\\Pictures";
    String sha256 = "a".repeat(64);

    AllowedRootEntity allowedRoot = new AllowedRootEntity(canonicalPath, false, scanTime);
    entityManager.persist(allowedRoot);

    FileRecordEntity fileRecord =
        new FileRecordEntity(
            "holidays\\photo.png",
            "photo.png",
            "png",
            2048L,
            sha256,
            allowedRoot,
            lastModifiedAt,
            scanTime);

    FileRecordEntity saved = fileRecordRepository.save(fileRecord);
    entityManager.flush();

    Long savedFileId = saved.getId();
    Long savedRootId = allowedRoot.getId();

    entityManager.clear();

    Optional<FileRecordEntity> found =
        fileRecordRepository.findByAllowedRootAndRelativePath(allowedRoot, "holidays\\photo.png");

    assertThat(savedFileId).isNotNull();
    assertThat(found).isPresent();

    FileRecordEntity loaded = found.orElseThrow();

    AllowedRootEntity loadedRoot = loaded.getAllowedRoot();
    Long loadedRootId = loadedRoot.getId();
    String loadedRootPath = loadedRoot.getCanonicalPath();

    assertField("id", savedFileId, loaded.getId());
    assertField("relativePath", "holidays\\photo.png", loaded.getRelativePath());
    assertField("originalName", "photo.png", loaded.getOriginalName());
    assertField("extension", "png", loaded.getExtension());
    assertField("sizeBytes", 2048L, loaded.getSizeBytes());
    assertField("lastModifiedAt", lastModifiedAt, loaded.getLastModifiedAt());
    assertField("sha256", sha256, loaded.getSha256());
    assertField("firstDiscoveredAt", scanTime, loaded.getFirstDiscoveredAt());
    assertField("lastSeenAt", scanTime, loaded.getLastSeenAt());
    assertField("allowedRoot.id", savedRootId, loadedRootId);
    assertField("allowedRoot.path", canonicalPath, loadedRootPath);

    assertThat(loaded.getRelativePath()).isEqualTo("holidays\\photo.png");
    assertThat(loaded.getOriginalName()).isEqualTo("photo.png");
    assertThat(loaded.getExtension()).isEqualTo("png");
    assertThat(loaded.getSizeBytes()).isEqualTo(2048L);
    assertThat(loaded.getLastModifiedAt()).isEqualTo(lastModifiedAt);
    assertThat(loaded.getSha256()).isEqualTo(sha256);
    assertThat(loaded.getFirstDiscoveredAt()).isEqualTo(scanTime);
    assertThat(loaded.getLastSeenAt()).isEqualTo(scanTime);
    assertThat(loaded.getAllowedRoot().getId()).isEqualTo(savedRootId);
    assertThat(loaded.getAllowedRoot().getCanonicalPath()).isEqualTo(canonicalPath);

    Optional<FileRecordEntity> missingPath =
        fileRecordRepository.findByAllowedRootAndRelativePath(allowedRoot, "holidays\\missing.png");
    assertField("missing path exists", false, missingPath.isPresent());
    assertThat(missingPath).isEmpty();

    AllowedRootEntity otherRoot =
        allowedRootRepository.save(new AllowedRootEntity("C:\\Test\\Other", false, scanTime));
    Optional<FileRecordEntity> missingRoot =
        fileRecordRepository.findByAllowedRootAndRelativePath(otherRoot, "holidays\\photo.png");
    assertField("other root has file", false, missingRoot.isPresent());
    assertThat(missingRoot).isEmpty();
  }
}
