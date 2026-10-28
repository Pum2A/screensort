package pl.pum.screensort.review;

import pl.pum.screensort.api.ApiExceptionHandler;
import static org.assertj.core.api.Assertions.*;

import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import pl.pum.screensort.gallery.AllowedRootEntity;
import pl.pum.screensort.gallery.FileRecordEntity;
import pl.pum.screensort.gallery.FileRecordRepository;
import pl.pum.screensort.naming.NameSuggestionGenerator;
import pl.pum.screensort.naming.NamingSettings;
import pl.pum.screensort.naming.NamingException;
import pl.pum.screensort.naming.NamingService;
import pl.pum.screensort.naming.database.FileNamingRepository;
import pl.pum.screensort.organization.BatchStore;
import pl.pum.screensort.organization.CategorySuggester;
import pl.pum.screensort.organization.export.ExportController;
import pl.pum.screensort.organization.export.ExportManager;
import pl.pum.screensort.organization.export.ExportStore;
import pl.pum.screensort.organization.export.OrganizedFileCopier;
import pl.pum.screensort.gallery.reading.ScanFolderValidator;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReviewService.class, ReviewStore.class, NamingService.class, NameSuggestionGenerator.class, NamingSettings.class,
    AnalysisStore.class, BatchStore.class, CategorySuggester.class, ReviewJpaTest.Config.class,
    ExportStore.class, ExportManager.class, OrganizedFileCopier.class, ScanFolderValidator.class})
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
  @Autowired ExportStore exports;
  @Autowired ExportManager exportManager;
  @Autowired JsonMapper json;

  @Test void approvesAndReloadsTheNameAndCategoryWithoutTouchingTheSource(@TempDir Path folder) throws Exception {
    Path source = folder.resolve("IMG_0026.png");
    Files.write(source, new byte[]{1, 2, 3, 4});
    var modified = Files.getLastModifiedTime(source);
    var fixture = prepare(folder);
    var initial = reviews.read(fixture.batchId(), fixture.fileId());
    assertThat(initial.proposedName()).isEqualTo("kubek_biurko.png"); // Polish by default.
    assertThat(initial.proposalUncertain()).isFalse();
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
    assertThat(store.all(fixture.batchId())).containsExactly(result);
    assertThat(naming.read(fixture.fileId()).approvedName()).isEqualTo("kubek.png");
    assertThat(files.findById(fixture.fileId()).orElseThrow().getOriginalName()).isEqualTo("IMG_0026.png");
    assertThat(Files.readAllBytes(source)).containsExactly(1, 2, 3, 4);
    assertThat(Files.getLastModifiedTime(source)).isEqualTo(modified);
    assertThat(Files.exists(folder.resolve("kubek.png"))).isFalse();
  }

  @Test void organizationPlanContainsOnlyCurrentApprovedItems(@TempDir Path folder) {
    var fixture = prepare(folder);
    assertThat(batches.plan(fixture.batchId(), 0).totalFiles()).isZero();

    var initial = reviews.read(fixture.batchId(), fixture.fileId());
    reviews.save(fixture.batchId(), fixture.fileId(), approve(initial, "kubek.png"));

    var plan = batches.plan(fixture.batchId(), 0);
    assertThat(plan.totalFiles()).isEqualTo(1);
    assertThat(plan.files()).containsExactly(new ReviewStore.PlanItem(
        fixture.fileId(), "IMG_0026.png", "IMG_0026.png", "PHOTOGRAPHS", "kubek.png"));

    var file = files.findById(fixture.fileId()).orElseThrow();
    file.updateFromScan(file.getOriginalName(), file.getExtension(), 5, "b".repeat(64), Instant.now(), Instant.now());
    entities.flush();
    assertThat(batches.plan(fixture.batchId(), 0).totalFiles()).isZero();
  }

  @Test void exportSnapshotRejectsALaterChangeInApprovalAndRecoversAsInterrupted(@TempDir Path folder) {
    var fixture = prepare(folder);
    var approved = reviews.save(fixture.batchId(), fixture.fileId(), approve(reviews.read(fixture.batchId(), fixture.fileId()), "first.png"));
    UUID id = UUID.randomUUID();
    exports.create(id, fixture.batchId(), folder.resolve("output").toString());
    var item = exports.next(id).orElseThrow();
    assertThat(item.approvedName()).isEqualTo("first.png");
    assertThat(exports.current(item)).isTrue();
    naming.approve(fixture.fileId(), approved.analysisId(), HASH, approved.namingRevision(), "second.png");
    assertThat(exports.current(item)).isFalse();
    exports.recover();
    assertThat(exports.find(id).orElseThrow().status()).isEqualTo("INTERRUPTED");
    assertThat(exports.latest(fixture.batchId()).orElseThrow().id()).isEqualTo(id);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void exportsApprovedImageThroughHttpAndPostgresToARealCategoryFolder(@TempDir Path directory) throws Exception {
    Path source = Files.createDirectory(directory.resolve("source"));
    Path destination = Files.createDirectory(directory.resolve("destination"));
    byte[] bytes = {1, 2, 3, 4, 5};
    Path original = Files.write(source.resolve("IMG_0026.png"), bytes);
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    var fixture = new TransactionTemplate(transactions).execute(status -> prepare(source, hash));
    reviews.save(fixture.batchId(), fixture.fileId(), approve(reviews.read(fixture.batchId(), fixture.fileId()), "kubek.png"));
    // Existing output belongs to the user and must not be modified.
    Path existing = Files.createDirectory(destination.resolve("ScreenSort"));
    Files.writeString(existing.resolve("keep.txt"), "keep");
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
        .standaloneSetup(new ExportController(exportManager, exports)).setControllerAdvice(new ApiExceptionHandler()).build();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/api/organization/batches/" + fixture.batchId() + "/exports")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("destinationFolder", destination.toString()))))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
    ExportStore.Job job;
    long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    do {
      job = exports.latest(fixture.batchId()).orElseThrow();
      if (!job.status().equals("RUNNING")) break;
      Thread.sleep(20);
    } while (System.nanoTime() < timeout);
    assertThat(job.status()).isEqualTo("COMPLETED");
    assertThat(job.copied()).isOne();
    assertThat(job.failed()).isZero();
    assertThat(Files.readAllBytes(Path.of(job.outputFolder()).resolve("Fotografie/kubek.png"))).isEqualTo(bytes);
    assertThat(Files.readAllBytes(original)).isEqualTo(bytes);
    assertThat(Files.readString(existing.resolve("keep.txt"))).isEqualTo("keep");
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/organization/exports/" + job.id()))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.copied").value(1));
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
    return prepare(folder, HASH);
  }
  private Fixture prepare(Path folder, String hash) {
    Instant now = Instant.parse("2026-10-19T12:00:00Z");
    var root = new AllowedRootEntity(folder.toAbsolutePath().toString(), false, now);
    entities.persist(root);
    long size;
    try {size = Files.exists(folder.resolve("IMG_0026.png")) ? Files.size(folder.resolve("IMG_0026.png")) : 4;}
    catch (java.io.IOException error) {throw new java.io.UncheckedIOException(error);}
    var file = new FileRecordEntity("IMG_0026.png", "IMG_0026.png", "png", size, hash, root, now, now);
    entities.persist(file);
    entities.flush();
    var visual = new AnalysisResult.Visual("A mug on a desk.", "test-only", "test-revision", "caption", "cpu", 1);
    var result = new AnalysisResult(hash,
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
