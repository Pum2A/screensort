package pl.pum.screensort.organization;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.analysis.result.SavedAnalysis;
import pl.pum.screensort.naming.NameSuggestion;
import pl.pum.screensort.naming.NameSuggestionGenerator;
import pl.pum.screensort.organization.review.ReviewStore;

/** Short SQL transactions only. Model inference never holds a connection or a row lock. */
@Service
public class BatchStore {
  public record Job(UUID id, String folderPath, String status, boolean ocr, boolean visual,
      long total, long completed, long failed, long processing, Instant createdAt) {}
  /** {@code attempts}: how many times this file already timed out (0 or 1). */
  public record Pending(long fileId, String originalName, String sourceSha256, int attempts) {
    public Pending(long fileId, String originalName, String sourceSha256) { this(fileId, originalName, sourceSha256, 0); }
  }

  private final JdbcTemplate jdbc;
  private final CategorySuggester categories;
  private final NameSuggestionGenerator names;
  private final ReviewStore reviews;

  public BatchStore(JdbcTemplate jdbc, CategorySuggester categories, NameSuggestionGenerator names, ReviewStore reviews) {
    this.jdbc = jdbc; this.categories = categories; this.names = names; this.reviews = reviews;
  }

  @Transactional
  public Job create(String folder, boolean ocr, boolean visual) {
    var roots = jdbc.queryForList("select id from allowed_root where canonical_path = ?", Long.class, folder);
    if (roots.isEmpty()) throw new AnalysisException(409, "Najpierw zeskanuj wybrany folder.");
    UUID id = UUID.randomUUID();
    jdbc.update("insert into analysis_batch(id, allowed_root_id, ocr_enabled, visual_enabled, status) values (?, ?, ?, ?, 'RUNNING')",
        id, roots.getFirst(), ocr, visual);
    jdbc.update("""
        insert into analysis_batch_item(batch_id, file_id, source_sha256, original_name)
        select ?, id, sha256, original_name from file_record where allowed_root_id = ?
        """, id, roots.getFirst());
    return find(id).orElseThrow();
  }

  @Transactional(readOnly = true)
  public Optional<Job> find(UUID id) {
    return jdbc.query("""
        select b.id, r.canonical_path, b.status, b.ocr_enabled, b.visual_enabled, b.created_at,
          count(i.file_id) as total,
          count(i.file_id) filter (where i.status = 'COMPLETED') as completed,
          count(i.file_id) filter (where i.status = 'FAILED') as failed,
          count(i.file_id) filter (where i.status = 'RUNNING') as processing
        from analysis_batch b join allowed_root r on r.id = b.allowed_root_id
        left join analysis_batch_item i on i.batch_id = b.id where b.id = ?
        group by b.id, r.canonical_path
        """, (row, n) -> new Job(row.getObject("id", UUID.class), row.getString("canonical_path"),
        row.getString("status"), row.getBoolean("ocr_enabled"), row.getBoolean("visual_enabled"),
        row.getLong("total"), row.getLong("completed"), row.getLong("failed"),
        row.getLong("processing"),
        row.getTimestamp("created_at").toInstant()), id).stream().findFirst();
  }

  @Transactional(readOnly = true)
  public Optional<Job> latest(String folder) {
    var ids = jdbc.queryForList("""
        select b.id from analysis_batch b join allowed_root r on r.id = b.allowed_root_id
        where r.canonical_path = ? order by b.created_at desc, b.id desc limit 1
        """, UUID.class, folder);
    return ids.isEmpty() ? Optional.empty() : find(ids.getFirst());
  }

  @Transactional
  public Optional<Pending> next(UUID id) {
    var pending = jdbc.query("""
        select file_id, original_name, source_sha256, attempts from analysis_batch_item
        where batch_id = ? and status = 'PENDING' order by attempts, file_id limit 1 for update
        """, (row, n) -> new Pending(row.getLong(1), row.getString(2), row.getString(3), row.getInt(4)), id);
    if (pending.isEmpty()) return Optional.empty();
    var item = pending.getFirst();
    jdbc.update("update analysis_batch_item set status = 'RUNNING' where batch_id = ? and file_id = ?", id, item.fileId());
    return Optional.of(item);
  }

  @Transactional(readOnly = true)
  public boolean isRunning(UUID id) {
    return Boolean.TRUE.equals(jdbc.queryForObject("select status = 'RUNNING' from analysis_batch where id = ?", Boolean.class, id));
  }

  @Transactional
  public void completed(UUID id, Pending item, SavedAnalysis saved) {
    if (saved.result().ocr().result() == null && saved.result().visual().result() == null) {
      failed(id, item.fileId(), "Modele nie zwróciły poprawnego wyniku. Spróbuj ponownie po sprawdzeniu pakietu AI.", false);
      return;
    }
    var ocr = saved.result().ocr().result();
    var visual = saved.result().visual().result();
    var proposal = names.suggest(item.originalName(), ocr == null ? null : ocr.text(),
        visual == null ? null : visual.description());
    boolean partial = saved.result().ocr().error() != null || saved.result().visual().error() != null;
    jdbc.update("""
        update analysis_batch_item set status = 'COMPLETED', analysis_id = ?, category = ?, proposed_name = ?,
          proposal_uncertain = ?, message = ?
        where batch_id = ? and file_id = ?
        """, saved.id(), categories.suggest(saved.result()), proposal.map(NameSuggestion::name).orElse(null),
        proposal.map(NameSuggestion::uncertain).orElse(false),
        partial ? "Część analizy nie powiodła się. Sprawdź szczegóły obrazu." : null, id, item.fileId());
  }

  @Transactional
  public void failed(UUID id, long fileId, String message, boolean retry) {
    jdbc.update("update analysis_batch_item set status = ?, message = ? where batch_id = ? and file_id = ?",
        retry ? "PENDING" : "FAILED", message, id, fileId);
  }

  /** Back to the queue behind every file not tried yet, with one more attempt counted. */
  @Transactional
  public void postpone(UUID id, long fileId, String message) {
    jdbc.update("""
        update analysis_batch_item set status = 'PENDING', attempts = attempts + 1, message = ?
        where batch_id = ? and file_id = ?
        """, message, id, fileId);
  }

  @Transactional
  public void state(UUID id, String state) {
    if (!List.of("PAUSED", "CANCELLED", "COMPLETED").contains(state)) throw new IllegalArgumentException("Invalid state");
    jdbc.update("update analysis_batch set status = ?, updated_at = current_timestamp where id = ? and status = 'RUNNING'", state, id);
  }

  @Transactional
  public Job resume(UUID id) {
    jdbc.update("update analysis_batch set status = 'RUNNING', updated_at = current_timestamp where id = ? and status = 'PAUSED'", id);
    return find(id).orElseThrow(() -> new AnalysisException(404, "Zadanie nie istnieje."));
  }

  @Transactional
  public void recover() {
    jdbc.update("update analysis_batch set status = 'PAUSED' where status = 'RUNNING'");
    jdbc.update("update analysis_batch_item set status = 'PENDING' where status = 'RUNNING'");
  }

  @Transactional
  public void release(UUID id) {
    jdbc.update("update analysis_batch_item set status = 'PENDING' where batch_id = ? and status = 'RUNNING'", id);
    state(id, "PAUSED");
  }

  @Transactional(readOnly = true)
  public ReviewStore.Page items(UUID id, int page) {
    return reviews.page(id, page);
  }

  @Transactional(readOnly = true)
  public ReviewStore.PlanPage plan(UUID id, int page) {
    return reviews.plan(id, page);
  }
}
