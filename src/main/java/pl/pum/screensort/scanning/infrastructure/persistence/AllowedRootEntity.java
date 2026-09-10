package pl.pum.screensort.scanning.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pl.pum.screensort.scanning.domain.AllowedRoot;

import java.nio.file.Path;
import java.time.Instant;

@Entity
@Table(name = "allowed_root")
class AllowedRootEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_path", nullable = false, unique = true, length = 2048)
    private String canonicalPath;

    @Column(name = "recursive_scan", nullable = false)
    private boolean recursiveScan;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AllowedRootEntity() {
    }

    AllowedRootEntity(AllowedRoot allowedRoot) {
        updateFrom(allowedRoot);
    }

    void updateFrom(AllowedRoot allowedRoot) {
        this.canonicalPath = allowedRoot.canonicalPath().toString();
        this.recursiveScan = allowedRoot.recursiveScan();
    }

    AllowedRoot toDomain() {
        return new AllowedRoot(Path.of(this.canonicalPath), this.recursiveScan);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

}
