package pl.pum.screensort.analysis.database;

import jakarta.persistence.*;
import java.time.Instant;
import pl.pum.screensort.gallery.FileRecordEntity;

@Entity
@Table(name = "file_analysis")
public class FileAnalysisEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "file_record_id", nullable = false) private FileRecordEntity file;
  @Column(name = "source_sha256", nullable = false, length = 64) private String sourceSha256;
  @Column(name = "ocr_enabled", nullable = false) private boolean ocrEnabled;
  @Column(name = "visual_enabled", nullable = false) private boolean visualEnabled;
  @Column(name = "result_json", nullable = false, columnDefinition = "text") private String resultJson;
  @Column(name = "analyzed_at", nullable = false) private Instant analyzedAt;

  protected FileAnalysisEntity() {}

  public FileAnalysisEntity(FileRecordEntity file, boolean ocr, boolean visual) {
    this.file = file; this.ocrEnabled = ocr; this.visualEnabled = visual;
  }

  public void update(String sha256, String json, Instant now) {
    this.sourceSha256 = sha256; this.resultJson = json; this.analyzedAt = now;
  }

  public Long getId() { return id; }
  public FileRecordEntity getFile() { return file; }
  public String getSourceSha256() { return sourceSha256; }
  public String getResultJson() { return resultJson; }
  public Instant getAnalyzedAt() { return analyzedAt; }
}
