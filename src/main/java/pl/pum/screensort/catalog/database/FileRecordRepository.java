package pl.pum.screensort.catalog.database;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import pl.pum.screensort.scanning.knownfiles.KnownFile;

public interface FileRecordRepository extends JpaRepository<FileRecordEntity, Long> {
  Optional<FileRecordEntity> findByAllowedRootAndRelativePath(
      AllowedRootEntity allowedRoot, String relativePath);

  Page<FileRecordEntity> findAllByAllowedRootOrderByRelativePathAsc(
      AllowedRootEntity allowedRoot, Pageable pageable);

  // Spring Data escapes % and _ in the search text, so it is matched literally.
  Page<FileRecordEntity> findAllByAllowedRootAndOriginalNameContainingIgnoreCaseOrderByRelativePathAsc(
      AllowedRootEntity allowedRoot, String name, Pageable pageable);

  List<KnownFile> findAllByAllowedRoot_Id(Long rootId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select f from FileRecordEntity f where f.id = :id")
  Optional<FileRecordEntity> findForNamingById(@Param("id") long id);
}
