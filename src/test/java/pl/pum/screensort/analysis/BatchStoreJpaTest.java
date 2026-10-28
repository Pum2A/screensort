package pl.pum.screensort.analysis;

import pl.pum.screensort.suggestions.CategorySuggester;
import static org.assertj.core.api.Assertions.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import pl.pum.screensort.gallery.AllowedRootEntity;
import pl.pum.screensort.gallery.FileRecordEntity;
import pl.pum.screensort.suggestions.NameSuggestionGenerator;
import pl.pum.screensort.suggestions.NamingSettings;
import pl.pum.screensort.review.ReviewStore;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({BatchStore.class, ReviewStore.class, CategorySuggester.class, NameSuggestionGenerator.class, NamingSettings.class, BatchStoreJpaTest.JdbcConfig.class})
class BatchStoreJpaTest {
  static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    String nativeUrl = System.getenv("SCREENSORT_NAMING_TEST_JDBC_URL");
    if (nativeUrl != null) {
      if (!nativeUrl.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/screensort_naming_test_[a-f0-9]+")) {
        throw new IllegalArgumentException("Only an isolated test database is allowed");
      }
      properties.add("spring.datasource.url", () -> nativeUrl);
      properties.add("spring.datasource.username", () -> "naming_test");
      properties.add("spring.datasource.password", () -> System.getenv("SCREENSORT_NAMING_TEST_DB_PASSWORD"));
    } else {
      postgres.start();
      properties.add("spring.datasource.url", postgres::getJdbcUrl);
      properties.add("spring.datasource.username", postgres::getUsername);
      properties.add("spring.datasource.password", postgres::getPassword);
    }
  }
  @AfterAll static void close() { if (postgres.isRunning()) postgres.stop(); }
  @TestConfiguration static class JdbcConfig {
    @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
  }
  @Autowired BatchStore store;
  @Autowired EntityManager entityManager;

  @Test void snapshotsTheWholeFolderAndPagesWithoutLosingEntries() {
    String folder = prepare(55);
    var job = store.create(folder, true, true);
    assertThat(job.total()).isEqualTo(55);
    assertThat(store.items(job.id(), 0).items()).hasSize(50);
    assertThat(store.items(job.id(), 0).hasNext()).isTrue();
    assertThat(store.items(job.id(), 1).items()).hasSize(5);
    assertThat(store.items(job.id(), 1).hasNext()).isFalse();
    var first = store.next(job.id()).orElseThrow();
    store.failed(job.id(), first.fileId(), "Test failure", false);
    assertThat(store.find(job.id()).orElseThrow().failed()).isOne();
    assertThat(store.next(job.id()).orElseThrow().fileId()).isNotEqualTo(first.fileId());
  }

  @Test void recoversOnlyUnfinishedWorkAndKeepsTheSameJobId() {
    String folder = prepare(2);
    var job = store.create(folder, true, false);
    var first = store.next(job.id()).orElseThrow();
    store.failed(job.id(), first.fileId(), "Unavailable", true);
    store.state(job.id(), "PAUSED");
    assertThat(store.resume(job.id()).id()).isEqualTo(job.id());
    assertThat(store.next(job.id()).orElseThrow().fileId()).isEqualTo(first.fileId());
    store.recover();
    assertThat(store.find(job.id()).orElseThrow().status()).isEqualTo("PAUSED");
    assertThat(store.items(job.id(), 0).items()).allMatch(item -> item.status().equals("PENDING"));
    assertThat(store.latest(folder).orElseThrow().id()).isEqualTo(job.id());
  }

  @Test void aPostponedFileComesAfterEveryFreshOneWithItsAttemptCounted() {
    String folder = prepare(3);
    var job = store.create(folder, true, false);
    var slow = store.next(job.id()).orElseThrow();
    assertThat(slow.attempts()).isZero();
    store.postpone(job.id(), slow.fileId(), "Retry later");

    var second = store.next(job.id()).orElseThrow();
    store.failed(job.id(), second.fileId(), "Bad image", false);
    var third = store.next(job.id()).orElseThrow();
    store.failed(job.id(), third.fileId(), "Bad image", false);
    var last = store.next(job.id()).orElseThrow();

    assertThat(second.fileId()).isNotEqualTo(slow.fileId());
    assertThat(third.fileId()).isNotEqualTo(slow.fileId());
    assertThat(last.fileId()).isEqualTo(slow.fileId());
    assertThat(last.attempts()).isOne();
  }

  private String prepare(int count) {
    String folder = "C:\\synthetic-batch-" + UUID.randomUUID();
    Instant now = Instant.parse("2026-10-19T12:00:00Z");
    var root = new AllowedRootEntity(folder, false, now);
    entityManager.persist(root);
    for (int index = 0; index < count; index++) {
      String name = "image-" + index + ".png";
      entityManager.persist(new FileRecordEntity(name, name, "png", 4, "a".repeat(64), root, now, now));
    }
    entityManager.flush();
    return folder;
  }
}
