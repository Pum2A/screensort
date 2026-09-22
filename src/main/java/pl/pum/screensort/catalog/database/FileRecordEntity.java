package pl.pum.screensort.catalog.database;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "file_record")
public class FileRecordEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "allowed_root_id", nullable = false)
  private AllowedRootEntity allowedRoot;

  @Column(name = "relative_path", nullable = false, length = 2048)
  private String relativePath;

  @Column(name = "original_name", nullable = false, length = 255)
  private String originalName;

  @Column(name = "extension", nullable = false, length = 16)
  private String extension;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "last_modified_at", nullable = false)
  private Instant lastModifiedAt;

  @Column(name = "sha256", nullable = false, length = 64)
  private String sha256;

  @Column(name = "first_discovered_at", nullable = false)
  private Instant firstDiscoveredAt;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  protected FileRecordEntity() {}

  public FileRecordEntity(
      String relativePath,
      String originalName,
      String extension,
      long sizeBytes,
      String sha256,
      AllowedRootEntity allowedRoot,
      Instant lastModifiedAt,
      Instant now) {
    this.allowedRoot = allowedRoot;
    this.relativePath = relativePath;
    this.originalName = originalName;
    this.extension = extension;
    this.sizeBytes = sizeBytes;
    this.sha256 = sha256;
    this.lastModifiedAt = lastModifiedAt;
    this.lastSeenAt = now;
    this.firstDiscoveredAt = now;
  }

  public Long getId() {
    return id;
  }

  public AllowedRootEntity getAllowedRoot() {
    return allowedRoot;
  }

  public String getRelativePath() {
    return relativePath;
  }

  public String getOriginalName() {
    return originalName;
  }

  public String getExtension() {
    return extension;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public Instant getLastModifiedAt() {
    return lastModifiedAt;
  }

  public String getSha256() {
    return sha256;
  }

  public Instant getFirstDiscoveredAt() {
    return firstDiscoveredAt;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }
}
