package pl.pum.screensort.catalog.database;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
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

  @Test
  void savesAndReadsAllowedRoot() {
    Instant now = Instant.parse("2026-09-22T12:00:00Z");
    String canonicalPath = "C:\\Test\\Pictures";

    AllowedRootEntity allowedRoot = new AllowedRootEntity(canonicalPath, false, now);

    // ACT — zapisujemy encję do PostgreSQL
    entityManager.persist(allowedRoot);
    entityManager.flush();

    Long savedId = allowedRoot.getId();

    // Usuwamy zapisaną encję z pamięci Hibernate
    entityManager.clear();

    // Odczytujemy ją ponownie z PostgreSQL
    AllowedRootEntity loaded = entityManager.find(AllowedRootEntity.class, savedId);

    // ASSERT — sprawdzamy otrzymane dane
    assertThat(savedId).isNotNull();
    assertThat(loaded).isNotNull();
    assertThat(loaded.getCanonicalPath()).isEqualTo(canonicalPath);
    assertThat(loaded.isRecursiveScan()).isFalse();
    assertThat(loaded.getCreatedAt()).isEqualTo(now);
    assertThat(loaded.getUpdatedAt()).isEqualTo(now);
  }
}
