package pl.pum.screensort.review;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.pum.screensort.organization.ApprovedFilesSql;

@Repository
public class ReviewStore {
  public record Page(List<ReviewItem> items, boolean hasNext) {}
  public record PlanItem(long fileId, String relativePath, String originalName,
      String category, String approvedName) {}
  public record PlanPage(List<PlanItem> files, int pageNumber, int pageSize,
      long totalFiles, boolean hasNext) {}

  private static final String SELECT = """
      select i.file_id, i.original_name, i.status, i.category, i.proposed_name, i.proposal_uncertain, i.message,
        i.analysis_id, i.source_sha256, f.sha256 as current_sha, a.source_sha256 as analysis_sha,
        n.revision as naming_revision, n.approved_name, n.source_sha256 as naming_sha,
        r.decision, r.category as review_category, r.reviewed_at, r.revision as review_revision,
        r.source_sha256 as review_sha, r.naming_revision as reviewed_naming_revision
      from analysis_batch_item i join file_record f on f.id = i.file_id
      left join file_analysis a on a.id = i.analysis_id
      left join file_naming n on n.file_record_id = i.file_id
      left join file_organization_review r on r.file_record_id = i.file_id
      """;

  private final JdbcTemplate jdbc;

  public ReviewStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional(readOnly = true)
  public Page page(UUID batchId, int page) {
    var rows = jdbc.query(SELECT + " where i.batch_id = ? order by i.file_id limit 51 offset ?",
        this::item, batchId, Math.multiplyExact((long) page, 50));
    return new Page(List.copyOf(rows.subList(0, Math.min(50, rows.size()))), rows.size() > 50);
  }

  /** Every item of the batch at once, for the library screen (the catalog caps a folder at 1000 files). */
  @Transactional(readOnly = true)
  public List<ReviewItem> all(UUID batchId) {
    return List.copyOf(jdbc.query(SELECT + " where i.batch_id = ? order by i.file_id", this::item, batchId));
  }

  /** Read-only export preview: only current, explicitly approved decisions are included. */
  @Transactional(readOnly = true)
  public PlanPage plan(UUID batchId, int page) {
    String from = ApprovedFilesSql.FROM;
    Long total = jdbc.queryForObject("select count(*) " + from, Long.class, batchId);
    var rows = jdbc.query("""
        select f.id, f.relative_path, f.original_name, r.category, n.approved_name
        """ + from + " order by f.relative_path, f.id limit 51 offset ?",
        (row, index) -> new PlanItem(row.getLong("id"), row.getString("relative_path"),
            row.getString("original_name"), row.getString("category"), row.getString("approved_name")),
        batchId, Math.multiplyExact((long) page, 50));
    return new PlanPage(List.copyOf(rows.subList(0, Math.min(50, rows.size()))),
        page, 50, total == null ? 0 : total, rows.size() > 50);
  }

  @Transactional(readOnly = true)
  public Optional<ReviewItem> item(UUID batchId, long fileId) {
    return jdbc.query(SELECT + " where i.batch_id = ? and i.file_id = ?", this::item, batchId, fileId)
        .stream().findFirst();
  }

  /** Caller holds the file_record write lock and has compared both revisions. */
  @Transactional
  public void save(long fileId, long analysisId, String sha256, String decision,
      String category, Long namingRevision) {
    jdbc.update("""
        insert into file_organization_review
          (file_record_id, analysis_id, source_sha256, decision, category, naming_revision, reviewed_at)
        values (?, ?, ?, ?, ?, ?, current_timestamp)
        on conflict (file_record_id) do update set
          analysis_id = excluded.analysis_id, source_sha256 = excluded.source_sha256,
          decision = excluded.decision, category = excluded.category,
          naming_revision = excluded.naming_revision, reviewed_at = excluded.reviewed_at,
          revision = file_organization_review.revision + 1
        """, fileId, analysisId, sha256, decision, category, namingRevision);
  }

  private ReviewItem item(ResultSet row, int index) throws SQLException {
    String currentSha = row.getString("current_sha");
    String decision = row.getString("decision");
    Long namingRevision = row.getObject("naming_revision", Long.class);
    Long reviewedNamingRevision = row.getObject("reviewed_naming_revision", Long.class);
    boolean decisionMatches = currentSha.equals(row.getString("review_sha"))
        && ("SKIPPED".equals(decision) || currentSha.equals(row.getString("naming_sha"))
            && namingRevision != null && namingRevision.equals(reviewedNamingRevision));
    var reviewedAt = row.getTimestamp("reviewed_at");
    var review = decision == null ? null : new ReviewItem.Decision(decision,
        row.getString("review_category"), reviewedAt.toInstant(), row.getLong("review_revision"), decisionMatches);
    return new ReviewItem(row.getLong("file_id"), row.getString("original_name"), row.getString("status"),
        row.getString("category"), row.getString("proposed_name"), row.getBoolean("proposal_uncertain"),
        row.getString("message"),
        row.getObject("analysis_id", Long.class), row.getString("source_sha256"),
        currentSha.equals(row.getString("source_sha256")) && currentSha.equals(row.getString("analysis_sha")),
        namingRevision == null ? -1 : namingRevision,
        currentSha.equals(row.getString("naming_sha")) ? row.getString("approved_name") : null, review);
  }
}
