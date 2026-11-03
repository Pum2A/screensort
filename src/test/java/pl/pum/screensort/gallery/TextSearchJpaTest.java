package pl.pum.screensort.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TextSearchJpaTest {
  static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    postgres.start();
    properties.add("spring.datasource.url", postgres::getJdbcUrl);
    properties.add("spring.datasource.username", postgres::getUsername);
    properties.add("spring.datasource.password", postgres::getPassword);
  }

  @AfterAll
  static void stopContainer() {
    if (postgres.isRunning()) postgres.stop();
  }

  @Autowired JdbcTemplate jdbc;

  private static String result(String hash, String text) {
    return """
        {"source_sha256":"%s","ocr":{"status":"COMPLETED","result":{"text":"%s","engine":"rapidocr",
        "engine_version":"1","duration_ms":5}},"visual":{"status":"SKIPPED"}}""".formatted(hash, text);
  }

  @Test void findsTheLatestTextOfTheCurrentContentOnly() {
    Instant now = Instant.now();
    long root = jdbc.queryForObject("""
        insert into allowed_root(canonical_path, recursive_scan, created_at, updated_at)
        values ('C:\\\\Screens', false, now(), now()) returning id""", Long.class);
    long invoice = file(root, "a.png", "a".repeat(64));
    long changed = file(root, "b.png", "b".repeat(64));
    jdbc.update("insert into file_analysis(file_record_id, source_sha256, ocr_enabled, visual_enabled, result_json, analyzed_at) values (?, ?, true, false, ?, now() - interval '1 hour')",
        invoice, "a".repeat(64), result("a".repeat(64), "stara notatka"));
    jdbc.update("insert into file_analysis(file_record_id, source_sha256, ocr_enabled, visual_enabled, result_json, analyzed_at) values (?, ?, true, true, ?, now())",
        invoice, "a".repeat(64), result("a".repeat(64), "FAKTURA VAT nr 12"));
    // Analysed before the file changed: its old text must not be found.
    jdbc.update("insert into file_analysis(file_record_id, source_sha256, ocr_enabled, visual_enabled, result_json, analyzed_at) values (?, ?, true, false, ?, now())",
        changed, "c".repeat(64), result("c".repeat(64), "faktura z zeszłego roku"));

    var search = new TextSearch(null, jdbc, JsonMapper.builder().build());
    var files = List.of(dto(invoice, root, "a.png", "a".repeat(64), now), dto(changed, root, "b.png", "b".repeat(64), now));
    var hits = search.search(files, "faktura");

    assertThat(hits).extracting(TextSearch.Hit::fileId).containsExactly(invoice);
    assertThat(hits.getFirst().match()).isEqualTo("FAKTURA");
    assertThat(search.search(files, "notatka")).isEmpty();
  }

  private long file(long root, String name, String hash) {
    return jdbc.queryForObject("""
        insert into file_record(allowed_root_id, relative_path, original_name, extension, size_bytes,
          last_modified_at, sha256, first_discovered_at, last_seen_at)
        values (?, ?, ?, '.png', 10, now(), ?, now(), now()) returning id""", Long.class, root, name, name, hash);
  }

  private static CatalogFileDto dto(long id, long root, String name, String hash, Instant now) {
    return new CatalogFileDto(id, root, "C:\\Screens", name, name, ".png", 10, now, hash, now, now);
  }
}
