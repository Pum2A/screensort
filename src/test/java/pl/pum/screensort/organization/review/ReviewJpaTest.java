package pl.pum.screensort.organization.review;

import static org.assertj.core.api.Assertions.*;

import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import pl.pum.screensort.analysis.database.AnalysisStore;
import pl.pum.screensort.analysis.result.AnalysisResult;
import pl.pum.screensort.catalog.database.AllowedRootEntity;
import pl.pum.screensort.catalog.database.FileRecordEntity;
import pl.pum.screensort.catalog.database.FileRecordRepository;
import pl.pum.screensort.naming.NameSuggestionGenerator;
import pl.pum.screensort.naming.NamingException;
import pl.pum.screensort.naming.NamingService;
import pl.pum.screensort.naming.database.FileNamingRepository;
import pl.pum.screensort.organization.BatchStore;
import pl.pum.screensort.organization.CategorySuggester;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReviewService.class, ReviewStore.class, NamingService.class, NameSuggestionGenerator.class,
    AnalysisStore.class, BatchStore.class, CategorySuggester.class, ReviewJpaTest.Config.class})
class ReviewJpaTest {
  private static final String HASH = "a".repeat(64);
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
  @TestConfiguration static class Config {
    @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
    @Bean JsonMapper jsonMapper() { return JsonMapper.builder().build(); }
  }

  @Autowired EntityManager entities;
  @Autowired ReviewService reviews;
  @Autowired ReviewStore store;
  @Autowired NamingService naming;
  @Autowired FileNamingRepository names;
  @Autowired FileRecordRepository files;
  @Autowired AnalysisStore analyses;
  @Autowired BatchStore batches;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;

  @Test void approvesAndReloadsTheNameAndCategoryWithoutTouchingTheSource(@TempDir Path folder) throws Exception {
    Path source = folder.resolve("IMG_0026.png");
    Files.write(source, new byte[]{1, 2, 3, 4});
    var modified = Files.getLastModifiedTime(source);
    var fixture = prepare(folder);
    var initial = reviews.read(fixture.batchId(), fixture.fileId());
    assertThat(initial.namingRevision()).isEqualTo(-1);
    assertThat(initial.review()).isNull();
    var result = reviews.save(fixture.batchId(), fixture.fileId(), approve(initial, "kubek.png"));
    assertThat(result.review().decision()).isEqualTo("APPROVED");
    assertThat(result.review().category()).isEqualTo("PHOTOGRAPHS");
    assertThat(result.review().matchesCatalog()).isTrue();
    assertThat(result.review().revision()).isZero();
    assertThat(result.review().reviewedAt()).isNotNull();
    assertThat(result.namingRevision()).isZero();
    entities.clear();
    assertThat(reviews.read(fixture.batchId(), fixture.fileId())).isEqualTo(result);
    assertThat(batches.items(fixture.batchId(), 0).items()).containsExactly(result);
    assertThat(naming.read(fixture.fileId()).approvedName()).isEqualTo("kubek.png");
    assertThat(files.findById(fixture.fileId()).orElseThrow().getOriginalName()).isEqualTo("IMG_0026.png");
    assertThat(Files.readAllBytes(source)).containsExactly(1, 2, 3, 4);
    assertThat(Files.getLastModifiedTime(source)).isEqualTo(modified);
    assertThat(Files.exists(folder.resolve("kubek.png"))).isFalse();
  }

  @Test void skipsAndReturnsToApprovalWithoutDeletingAnExistingName(@TempDir Path folder) {
    var fixture = prepare(folder);
    var first = reviews.read(fixture.batchId(), fixture.fileId());
    var approved = reviews.save(fixture.batchId(), fixture.fileId(), approve(first, "first.png"));
    var skipped = reviews.save(fixture.batchId(), fixture.fileId(), request(approved, "SKIPPED", null, null));
    assertThat(skipped.review().decision()).isEqualTo("SKIPPED");
    assertThat(skipped.review().category()).isNull();
    assertThat(skipped.review().revision()).isEqualTo(1);
    assertThat(skipped.approvedName()).isEqualTo("first.png");
    assertThat(skipped.namingRevision()).isEqualTo(approved.namingRevision());
    var restored = reviews.save(fixture.batchId(), fixture.fileId(), approve(skipped, "second.png"));
    assertThat(restored.review().revision()).isEqualTo(2);
    assertThat(restored.review().matchesCatalog()).isTrue();
    assertThat(restored.approvedName()).isEqualTo("second.png");
  }

  @Test void rejectsAnOldFormRatherThanOverwritingTheCurrentDecision(@TempDir Path folder) {
    var fixture = prepare(folder);
    var first = reviews.read(fixture.batchId(), fixture.fileId());
    var approved = reviews.save(fixture.batchId(), fixture.fileId(), approve(first, "first.png"));
    assertThatThrownBy(() -> reviews.save(fixture.batchId(), fixture.fileId(), approve(first, "second.png")))
        .isInstanceOfSatisfying(ReviewException.class, error -> assertThat(error.code()).isEqualTo("REVIEW_CHANGED"));
    assertThat(store.item(fixture.batchId(), fixture.fileId()).orElseThrow()).isEqualTo(approved);
  }

  @Test void aSeparateNameEditInvalidatesTheOrganizationApproval(@TempDir Path folder) {
    var fixture = prepare(folder);
    var first = reviews.read(fixture.batchId(), fixture.fileId());
    var approved = reviews.save(fixture.batchId(), fixture.fileId(), approve(first, "first.png"));
    naming.approve(fixture.fileId(), first.analysisId(), HASH, approved.namingRevision(), "separate.png");
    var changed = reviews.read(fixture.batchId(), fixture.fileId());
    assertThat(changed.review().matchesCatalog()).isFalse();
    assertThat(changed.approvedName()).isEqualTo("separate.png");
    assertThat(reviews.save(fixture.batchId(), fixture.fileId(), approve(changed, "separate.png")).review().matchesCatalog()).isTrue();
  }

  @Test void changedContentIsNotApprovedUsingAnOldAnalysis(@TempDir Path folder) {
    var fixture = prepare(folder);
    var first = reviews.read(fixture.batchId(), fixture.fileId());
    reviews.save(fixture.batchId(), fixture.fileId(), approve(first, "first.png"));
    var file = files.findById(fixture.fileId()).orElseThrow();
    file.updateFromScan(file.getOriginalName(), file.getExtension(), 5, "b".repeat(64), Instant.now(), Instant.now());
    entities.flush();
    var changed = reviews.read(fixture.batchId(), fixture.fileId());
    assertThat(changed.analysisMatchesCatalog()).isFalse();
    assertThat(changed.review().matchesCatalog()).isFalse();
    assertThat(changed.approvedName()).isNull();
    assertThatThrownBy(() -> reviews.save(fixture.batchId(), fixture.fileId(), approve(changed, "second.png")))
        .isInstanceOfSatisfying(ReviewException.class, error -> assertThat(error.code()).isEqualTo("SOURCE_CHANGED"));
  }

  @Test void unsafeNamesDoNotCreateAnOrganizationDecision(@TempDir Path folder) {
    var fixture = prepare(folder);
    var first = reviews.read(fixture.batchId(), fixture.fileId());
    assertThatThrownBy(() -> reviews.save(fixture.batchId(), fixture.fileId(), approve(first, "../escape.png")))
        .isInstanceOfSatisfying(NamingException.class, error -> assertThat(error.code()).isEqualTo("INVALID_NAME"));
    assertThat(store.item(fixture.batchId(), fixture.fileId()).orElseThrow().review()).isNull();
    assertThat(names.findByFile_Id(fixture.fileId())).isEmpty();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void aFailedReviewWriteRollsBackTheJpaNameWriteToo(@TempDir Path folder) {
    var fixture = new TransactionTemplate(transactions).execute(status -> prepare(folder));
    var initial = reviews.read(fixture.batchId(), fixture.fileId());
    // Deliberate failure AFTER NamingService.saveAndFlush; this disposable DB is owned by the test.
    jdbc.execute("alter table file_organization_review add constraint ck_test_review_rollback check (file_record_id <> " + fixture.fileId() + ")");
    try {
      assertThatThrownBy(() -> reviews.save(fixture.batchId(), fixture.fileId(), approve(initial, "rollback.png")))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      assertThat(names.findByFile_Id(fixture.fileId())).isEmpty();
      assertThat(reviews.read(fixture.batchId(), fixture.fileId()).review()).isNull();
    } finally { jdbc.execute("alter table file_organization_review drop constraint ck_test_review_rollback"); }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void twoConcurrentFirstApprovalsCannotBothWin(@TempDir Path folder) throws Exception {
    var fixture = new TransactionTemplate(transactions).execute(status -> prepare(folder));
    var initial = reviews.read(fixture.batchId(), fixture.fileId());
    var start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var tasks = List.of("first.png", "second.png").stream().map(name -> workers.submit(() -> {
        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Test timeout");
        try {
          reviews.save(fixture.batchId(), fixture.fileId(), approve(initial, name));
          return "SAVED";
        } catch (ReviewException error) { return error.code(); }
      })).toList();
      start.countDown();
      assertThat(List.of(tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("SAVED", "REVIEW_CHANGED");
    }
    var saved = reviews.read(fixture.batchId(), fixture.fileId());
    assertThat(saved.review().revision()).isZero();
    assertThat(saved.review().matchesCatalog()).isTrue();
    assertThat(saved.approvedName()).isIn("first.png", "second.png");
  }

  private record Fixture(java.util.UUID batchId, long fileId) {}
  private Fixture prepare(Path folder) {
    Instant now = Instant.parse("2026-10-19T12:00:00Z");
    var root = new AllowedRootEntity(folder.toAbsolutePath().toString(), false, now);
    entities.persist(root);
    var file = new FileRecordEntity("IMG_0026.png", "IMG_0026.png", "png", 4, HASH, root, now, now);
    entities.persist(file);
    entities.flush();
    var visual = new AnalysisResult.Visual("A mug on a desk.", "test-only", "test-revision", "caption", "cpu", 1);
    var result = new AnalysisResult(HASH,
        new AnalysisResult.OcrStep(AnalysisResult.Status.SKIPPED, null, null),
        new AnalysisResult.VisualStep(AnalysisResult.Status.COMPLETED, visual, null));
    var saved = analyses.save(file.getId(), false, true, result);
    var job = batches.create(root.getCanonicalPath(), false, true);
    batches.completed(job.id(), batches.next(job.id()).orElseThrow(), saved);
    batches.state(job.id(), "COMPLETED");
    return new Fixture(job.id(), file.getId());
  }

  private static ReviewService.Request approve(ReviewItem item, String name) {
    return request(item, "APPROVED", name, "PHOTOGRAPHS");
  }
  private static ReviewService.Request request(ReviewItem item, String decision, String name, String category) {
    return new ReviewService.Request(item.analysisId(), item.sourceSha256(),
        item.review() == null ? -1L : item.review().revision(), item.namingRevision(), decision, name, category);
  }
}
