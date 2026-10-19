package pl.pum.screensort.naming.database;

import jakarta.persistence.*;
import java.time.Instant;
import pl.pum.screensort.catalog.database.FileRecordEntity;

@Entity
@Table(name = "file_naming")
public class FileNamingEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "file_record_id", nullable = false, unique = true)
  private FileRecordEntity file;

  @Column(name = "analysis_id", nullable = false)
  private long analysisId;
  @Column(name = "source_sha256", nullable = false, length = 64)
  private String sourceSha256;
  @Column(name = "approved_name", nullable = false, length = 255)
  private String approvedName;
  @Column(name = "approved_at", nullable = false)
  private Instant approvedAt;
  @Version
  private long revision;

  protected FileNamingEntity() {}

  public FileNamingEntity(FileRecordEntity file) { this.file = file; }

  public void approve(long analysisId, String sha256, String name, Instant now) {
    this.analysisId = analysisId;
    this.sourceSha256 = sha256;
    this.approvedName = name;
    this.approvedAt = now;
  }

  public String getSourceSha256() { return sourceSha256; }
  public String getApprovedName() { return approvedName; }
  public Instant getApprovedAt() { return approvedAt; }
  public long getRevision() { return revision; }
}
