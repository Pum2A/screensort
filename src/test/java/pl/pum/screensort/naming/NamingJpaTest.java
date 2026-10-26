package pl.pum.screensort.naming;

import static org.assertj.core.api.Assertions.*;

import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import pl.pum.screensort.analysis.database.AnalysisStore;
import pl.pum.screensort.catalog.database.AllowedRootEntity;
import pl.pum.screensort.catalog.database.FileRecordEntity;
import pl.pum.screensort.catalog.database.FileRecordRepository;
import pl.pum.screensort.naming.database.FileNamingRepository;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NamingService.class, NameSuggestionGenerator.class, AnalysisStore.class, NamingJpaTest.JsonConfig.class})
class NamingJpaTest {
  static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    // Default: disposable Testcontainers DB. Optional: an explicitly isolated native test DB.
    String nativeUrl = System.getenv("SCREENSORT_NAMING_TEST_JDBC_URL");
    if (nativeUrl != null) {
      if (!nativeUrl.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/screensort_naming_test_[a-f0-9]+")) {
        throw new IllegalArgumentException("Only an isolated loopback naming test database is allowed");
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

  @AfterAll
  static void stopContainer() {
    if (postgres.isRunning()) postgres.stop();
  }

  @TestConfiguration
  static class JsonConfig {
    @Bean JsonMapper jsonMapper() { return JsonMapper.builder().build(); }
  }

  @Autowired private EntityManager entityManager;
  @Autowired private NamingService naming;
  @Autowired private AnalysisStore analyses;
  @Autowired private FileRecordRepository files;
  @Autowired private FileNamingRepository names;
  @Autowired private PlatformTransactionManager transactions;

  @Test
  void savesReloadsAndUpdatesOneDecisionWithoutRenamingTheSource(@TempDir Path folder) throws Exception {
    Path source = folder.resolve("IMG_0026.png");
    byte[] original = new byte[]{1, 2, 3, 4};
    Files.write(source, original);
    var modified = Files.getLastModifiedTime(source);
    long id = prepareFile(folder);
    var review = naming.read(id);
    assertThat(review.proposedName()).isEqualTo("mug_desk.png");
    var approved = naming.approve(id, review.analysisId(), review.sourceSha256(), review.revision(), "kubek_na_biurku.png");
    assertThat(approved.revision()).isZero();
    entityManager.clear();

    var reloaded = naming.read(id);
    assertThat(reloaded.approvedName()).isEqualTo("kubek_na_biurku.png");
    assertThat(reloaded.approvalMatchesCatalog()).isTrue();
    var changed = naming.approve(id, reloaded.analysisId(), reloaded.sourceSha256(), reloaded.revision(), "moj_kubek.png");
    assertThat(changed.revision()).isEqualTo(1);
    entityManager.clear();
    assertThat(naming.read(id).approvedName()).isEqualTo("moj_kubek.png");
    assertThat(files.findById(id).orElseThrow().getOriginalName()).isEqualTo("IMG_0026.png");
    assertThat(Files.readAllBytes(source)).isEqualTo(original);
    assertThat(Files.getLastModifiedTime(source)).isEqualTo(modified);
    assertThat(Files.exists(folder.resolve("moj_kubek.png"))).isFalse();
  }

  @Test
  void rejectsAnOldFormWithoutReplacingTheStoredDecision(@TempDir Path folder) {
    long id = prepareFile(folder);
    var review = naming.read(id);
    naming.approve(id, review.analysisId(), review.sourceSha256(), review.revision(), "first.png");
    assertThatThrownBy(() -> naming.approve(id, review.analysisId(), review.sourceSha256(), review.revision(), "second.png"))
        .isInstanceOfSatisfying(NamingException.class, error -> assertThat(error.code()).isEqualTo("REVIEW_CHANGED"));
    // The failed call marks this test transaction rollback-only; inspect the still-managed decision.
    assertThat(names.findByFile_Id(id).orElseThrow().getApprovedName()).isEqualTo("first.png");
  }

  @Test
  void retainsButMarksAnApprovalOutdatedAfterTheCatalogHashChanges(@TempDir Path folder) {
    long id = prepareFile(folder);
    var review = naming.read(id);
    naming.approve(id, review.analysisId(), review.sourceSha256(), review.revision(), "old.png");
    var file = files.findById(id).orElseThrow();
    file.updateFromScan(file.getOriginalName(), file.getExtension(), 5, "b".repeat(64), Instant.now(), Instant.now());
    entityManager.flush();
    entityManager.clear();
    var outdated = naming.read(id);
    assertThat(outdated.analysisMatchesCatalog()).isFalse();
    assertThat(outdated.approvalMatchesCatalog()).isFalse();
    assertThat(outdated.approvedName()).isEqualTo("old.png");
    assertThat(outdated.proposedName()).isNull();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void serializesConcurrentFirstApprovalsAndCommitsOnlyOneDecision(@TempDir Path folder) throws Exception {
    long id = new TransactionTemplate(transactions).execute(status -> prepareFile(folder));
    var review = naming.read(id);
    var start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var tasks = List.of("first.png", "second.png").stream().map(name -> workers.submit(() -> {
        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Test timeout");
        try {
          naming.approve(id, review.analysisId(), review.sourceSha256(), review.revision(), name);
          return "SAVED";
        } catch (NamingException error) {
          return error.code();
        }
      })).toList();
      start.countDown();
      assertThat(List.of(tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("SAVED", "REVIEW_CHANGED");
    }
    var reloaded = naming.read(id);
    assertThat(reloaded.approvedName()).isIn("first.png", "second.png");
    assertThat(reloaded.revision()).isZero();
    assertThat(names.findByFile_Id(id)).isPresent();
  }

  @Test
  void reusesOnlyAFinishedAnalysisOfTheSameContentAndOptions(@TempDir Path folder) {
    long id = prepareFile(folder);
    assertThat(analyses.reusable(id, false, true, "a".repeat(64))).isPresent();
    // Changed content, other options or a step that did not finish all mean the model has to run again.
    assertThat(analyses.reusable(id, false, true, "b".repeat(64))).isEmpty();
    assertThat(analyses.reusable(id, true, true, "a".repeat(64))).isEmpty();
    analyses.save(id, true, true, NamingServiceTest.analysis("a".repeat(64), true).result());
    assertThat(analyses.reusable(id, true, true, "a".repeat(64))).isEmpty();
  }

  private long prepareFile(Path folder) {
    Instant now = Instant.parse("2026-10-18T12:00:00Z");
    var root = new AllowedRootEntity(folder.toAbsolutePath().toString(), false, now);
    entityManager.persist(root);
    var file = new FileRecordEntity("IMG_0026.png", "IMG_0026.png", "png", 4,
        "a".repeat(64), root, now, now);
    entityManager.persist(file);
    entityManager.flush();
    analyses.save(file.getId(), false, true, NamingServiceTest.analysis("a".repeat(64), true).result());
    return file.getId();
  }
}
