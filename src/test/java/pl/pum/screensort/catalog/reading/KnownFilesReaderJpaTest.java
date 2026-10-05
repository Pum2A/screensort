package pl.pum.screensort.catalog.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import pl.pum.screensort.catalog.database.AllowedRootEntity;
import pl.pum.screensort.catalog.database.AllowedRootRepository;
import pl.pum.screensort.catalog.database.FileRecordEntity;
import pl.pum.screensort.scanning.folder.AllowedRoot;
import pl.pum.screensort.scanning.knownfiles.KnownFile;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(KnownFilesReader.class)
class KnownFilesReaderJpaTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));

  @Autowired private KnownFilesReader reader;
  @Autowired private EntityManager entityManager;
  @Autowired private AllowedRootRepository allowedRootRepository;

  @Test
  void returnsEmptyMapWithoutSavingAnUnknownFolder(@TempDir Path folder) throws IOException {
    Map<String, KnownFile> result = reader.read(new AllowedRoot(folder.toRealPath()));

    assertThat(result).isEmpty();
    assertThat(allowedRootRepository.count()).isZero();
  }

  @Test
  void returnsEmptyMapForAStoredFolderWithoutFiles(@TempDir Path folder) throws IOException {
    Path folderPath = folder.toRealPath();
    Instant now = Instant.parse("2026-10-05T12:00:00Z");
    entityManager.persist(new AllowedRootEntity(folderPath.toString(), false, now));
    entityManager.flush();
    entityManager.clear();

    assertThat(reader.read(new AllowedRoot(folderPath))).isEmpty();
  }

  @Test
  void readsFourFieldsOnlyForTheSelectedFolderAndReturnsAnUnmodifiableMap(
      @TempDir Path folder, @TempDir Path otherFolder) throws IOException {
    Path folderPath = folder.toRealPath();
    Instant now = Instant.parse("2026-10-05T12:00:00Z");
    Instant modifiedAt = Instant.parse("2026-10-04T08:30:00.123456Z");
    String nestedPath = Path.of("holidays", "photo.png").toString();

    AllowedRootEntity root = new AllowedRootEntity(folderPath.toString(), false, now);
    AllowedRootEntity otherRoot =
        new AllowedRootEntity(otherFolder.toRealPath().toString(), false, now);
    entityManager.persist(root);
    entityManager.persist(otherRoot);
    entityManager.persist(
        new FileRecordEntity(
            "photo.png", "photo.png", "png", 2048L, "a".repeat(64), root, modifiedAt, now));
    entityManager.persist(
        new FileRecordEntity(
            nestedPath, "photo.png", "png", 4096L, "b".repeat(64), root, modifiedAt, now));
    // Ten sam klucz w innym folderze nie może nadpisać danych wybranego folderu.
    entityManager.persist(
        new FileRecordEntity(
            "photo.png", "photo.png", "png", 8192L, "c".repeat(64), otherRoot, modifiedAt, now));
    entityManager.flush();
    entityManager.clear();

    Map<String, KnownFile> result = reader.read(new AllowedRoot(folderPath));
    KnownFile photo = new KnownFile("photo.png", 2048L, modifiedAt, "a".repeat(64));
    KnownFile nestedPhoto = new KnownFile(nestedPath, 4096L, modifiedAt, "b".repeat(64));

    System.out.println("Known files from database: " + result);
    assertThat(result)
        .containsExactlyInAnyOrderEntriesOf(Map.of("photo.png", photo, nestedPath, nestedPhoto));
    assertThatThrownBy(() -> result.put("extra.png", photo))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
