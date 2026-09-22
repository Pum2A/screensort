package pl.pum.screensort.catalog.database;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "allowed_root")
public class AllowedRootEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "canonical_path", nullable = false, length = 2048)
  private String canonicalPath;

  @Column(name = "recursive_scan", nullable = false)
  private boolean recursiveScan;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AllowedRootEntity() {}

  public AllowedRootEntity(String canonicalPath, boolean recursiveScan, Instant now) {
    this.canonicalPath = canonicalPath;
    this.recursiveScan = recursiveScan;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public Long getId() {
    return id;
  }

  public String getCanonicalPath() {
    return canonicalPath;
  }

  public boolean isRecursiveScan() {
    return recursiveScan;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
