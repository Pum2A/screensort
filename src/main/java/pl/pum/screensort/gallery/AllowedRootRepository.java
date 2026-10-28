package pl.pum.screensort.gallery;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllowedRootRepository extends JpaRepository<AllowedRootEntity, Long> {
    Optional<AllowedRootEntity> findByCanonicalPath(String canonicalPath);
}
