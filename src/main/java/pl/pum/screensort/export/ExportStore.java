package pl.pum.screensort.export;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.pum.screensort.review.ApprovedFilesSql;

@Repository
public class ExportStore {
  public record Job(UUID id, UUID batchId, String status, String outputFolder,
      long total, long copied, long failed, String message, Instant createdAt) {}
  public record Item(long fileId, String sourceFolder, String relativePath, String originalName,
      long sizeBytes, String sha256, String approvedName, String category,
      long namingRevision, long reviewRevision) {}
  public record Issue(String originalName, String message) {}
  public record Issues(List<Issue> items, boolean hasNext) {}

  private static final String JOB_SELECT = """
      select j.*, count(e.file_id) as total,
        count(e.file_id) filter (where e.status = 'COPIED') as copied,
        count(e.file_id) filter (where e.status = 'FAILED') as failed
      from organization_export_job j left join organization_export_item e on e.export_id = j.id
      """;
  private final JdbcTemplate jdbc;

  public ExportStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional
  public Job create(UUID id, UUID batchId, String outputFolder) {
    jdbc.update("insert into organization_export_job (id, batch_id, output_folder, status) values (?, ?, ?, 'RUNNING')",
        id, batchId, outputFolder);
    int count = jdbc.update("""
        insert into organization_export_item
          (export_id, file_id, source_folder, relative_path, original_name, size_bytes, source_sha256,
           approved_name, category, naming_revision, review_revision)
        select ?, f.id, u.canonical_path, f.relative_path, f.original_name, f.size_bytes, f.sha256,
          n.approved_name, r.category, n.revision, r.revision
        """ + ApprovedFilesSql.FROM, id, batchId);
    if (count == 0) throw new ExportException(409, "NO_APPROVALS", "Najpierw zatwierdź aktualne nazwy i kategorie obrazów.");
    return find(id).orElseThrow();
  }

  @Transactional(readOnly = true)
  public Optional<Job> find(UUID id) {
    return jdbc.query(JOB_SELECT + " where j.id = ? group by j.id", (row, index) -> new Job(
        row.getObject("id", UUID.class), row.getObject("batch_id", UUID.class), row.getString("status"),
        row.getString("output_folder"), row.getLong("total"), row.getLong("copied"), row.getLong("failed"),
        row.getString("message"), row.getTimestamp("created_at").toInstant()), id).stream().findFirst();
  }

  @Transactional(readOnly = true)
  public Optional<Job> latest(UUID batchId) {
    var ids = jdbc.query("select id from organization_export_job where batch_id = ? order by created_at desc, id desc limit 1",
        (row, index) -> row.getObject("id", UUID.class), batchId);
    return ids.isEmpty() ? Optional.empty() : find(ids.getFirst());
  }

  @Transactional
  public Optional<Item> next(UUID id) {
    var pending = jdbc.query("""
        select * from organization_export_item where export_id = ? and status = 'PENDING' order by file_id limit 1 for update
        """, (row, index) -> new Item(row.getLong("file_id"), row.getString("source_folder"),
        row.getString("relative_path"), row.getString("original_name"), row.getLong("size_bytes"),
        row.getString("source_sha256"), row.getString("approved_name"), row.getString("category"),
        row.getLong("naming_revision"), row.getLong("review_revision")), id);
    if (pending.isEmpty()) return Optional.empty();
    var item = pending.getFirst();
    // Zajęcie pliku w tej samej transakcji: drugi worker nie wypisze tego samego pliku.
    jdbc.update("update organization_export_item set status = 'RUNNING' where export_id = ? and file_id = ?",
        id, item.fileId());
    return Optional.of(item);
  }

  @Transactional(readOnly = true)
  public boolean current(Item item) {
    Long count = jdbc.queryForObject("""
        select count(*) from file_record f
        join file_naming n on n.file_record_id = f.id
        join file_organization_review r on r.file_record_id = f.id
        where f.id = ? and f.sha256 = ? and n.source_sha256 = f.sha256 and r.source_sha256 = f.sha256
          and n.revision = ? and r.revision = ? and r.naming_revision = n.revision
          and n.analysis_id = r.analysis_id and r.decision = 'APPROVED'
        """, Long.class, item.fileId(), item.sha256(), item.namingRevision(), item.reviewRevision());
    return count != null && count == 1;
  }

  @Transactional
  public void copied(UUID id, long fileId, String target) {
    jdbc.update("update organization_export_item set status = 'COPIED', target_relative_path = ? where export_id = ? and file_id = ?",
        target, id, fileId);
  }

  @Transactional
  public void failed(UUID id, long fileId, String message) {
    jdbc.update("update organization_export_item set status = 'FAILED', message = ? where export_id = ? and file_id = ?",
        message, id, fileId);
  }

  @Transactional
  public void finish(UUID id, String status, String message) {
    jdbc.update("update organization_export_job set status = ?, message = ?, finished_at = current_timestamp where id = ?",
        status, message, id);
  }

  @Transactional
  public void recover() {
    jdbc.update("""
        update organization_export_job set status = 'INTERRUPTED', finished_at = current_timestamp,
          message = 'Kopiowanie zostało przerwane. Zapisane kopie pozostały w folderze wynikowym.'
        where status = 'RUNNING'
        """);
    jdbc.update("update organization_export_item set status = 'PENDING' where status = 'RUNNING'");
  }

  @Transactional(readOnly = true)
  public Issues issues(UUID id, int page) {
    var rows = jdbc.query("""
        select original_name, message from organization_export_item
        where export_id = ? and status = 'FAILED' order by file_id limit 51 offset ?
        """, (row, index) -> new Issue(row.getString("original_name"), row.getString("message")),
        id, Math.multiplyExact((long) page, 50));
    return new Issues(List.copyOf(rows.subList(0, Math.min(50, rows.size()))), rows.size() > 50);
  }
}
