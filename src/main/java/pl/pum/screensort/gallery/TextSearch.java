package pl.pum.screensort.gallery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pum.screensort.analysis.AnalysisResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Finds images of one folder by the text read from them or by what the AI saw (app and topic).
 * Only an analysis of the file's current content counts, so a changed file never matches old text.
 */
@Service
public class TextSearch {
  public enum Source { TEXT, AI }

  /** {@code before + match + after} is a short excerpt around the first match, on one line. */
  public record Hit(long fileId, Source source, String before, String match, String after) {}

  static final int MAX_HITS = 20;
  private static final int BEFORE = 28;
  private static final int AFTER = 48;
  private static final Locale POLISH = Locale.forLanguageTag("pl");

  private final CatalogReader catalog;
  private final JdbcTemplate jdbc;
  private final JsonMapper json;

  public TextSearch(CatalogReader catalog, JdbcTemplate jdbc, JsonMapper json) {
    this.catalog = catalog; this.jdbc = jdbc; this.json = json;
  }

  @Transactional(readOnly = true)
  public List<Hit> search(String folderPath, String query) throws IOException {
    String needle = query == null ? "" : query.strip().replaceAll("\\s+", " ");
    if (needle.length() < 2 || needle.length() > 100) return List.of();
    return search(catalog.read(folderPath), needle);
  }

  /** The folder's files are already read; kept apart so a database test can call it directly. */
  List<Hit> search(List<CatalogFileDto> files, String needle) {
    if (files.isEmpty()) return List.of();
    Map<Long, String> current = new HashMap<>();
    for (CatalogFileDto file : files) current.put(file.id(), file.sha256());
    record Row(long fileId, String sha, String json) {}
    var rows = jdbc.query("""
        select distinct on (a.file_record_id) a.file_record_id, a.source_sha256, a.result_json
        from file_analysis a join file_record f on f.id = a.file_record_id
        where f.allowed_root_id = ?
        order by a.file_record_id, a.analyzed_at desc
        """, (row, n) -> new Row(row.getLong(1), row.getString(2), row.getString(3)), files.getFirst().allowedRootId());
    Map<Long, Hit> hits = new HashMap<>();
    String lowered = needle.toLowerCase(POLISH);
    for (Row row : rows) {
      if (!row.sha().equals(current.get(row.fileId()))) continue;
      AnalysisResult result;
      try {
        result = json.readValue(row.json(), AnalysisResult.class);
      } catch (RuntimeException e) {
        continue;
      }
      var ocr = result.ocr().result();
      Hit hit = ocr == null ? null : hit(row.fileId(), Source.TEXT, ocr.text(), lowered);
      var subject = result.visual().result() == null ? null : result.visual().result().subject();
      if (hit == null && subject != null) {
        hit = hit(row.fileId(), Source.AI, subject.app() + " · " + subject.topicPl(), lowered);
      }
      if (hit != null) hits.put(row.fileId(), hit);
    }
    var ordered = new ArrayList<Hit>();
    for (CatalogFileDto file : files) {
      Hit hit = hits.get(file.id());
      if (hit != null && ordered.size() < MAX_HITS) ordered.add(hit);
    }
    return ordered;
  }

  /** Polish letters keep their length when lowercased, so positions in the lowered text fit the original. */
  static Hit hit(long fileId, Source source, String text, String loweredNeedle) {
    String line = text.replaceAll("\\s+", " ").strip();
    int at = line.toLowerCase(POLISH).indexOf(loweredNeedle);
    if (at < 0) return null;
    int end = at + loweredNeedle.length();
    int from = Math.max(0, at - BEFORE);
    int to = Math.min(line.length(), end + AFTER);
    return new Hit(fileId, source, (from > 0 ? "…" : "") + line.substring(from, at), line.substring(at, end),
        line.substring(end, to) + (to < line.length() ? "…" : ""));
  }
}
