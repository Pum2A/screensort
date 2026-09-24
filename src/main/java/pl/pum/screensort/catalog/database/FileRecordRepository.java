package pl.pum.screensort.catalog.database;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRecordRepository extends JpaRepository<FileRecordEntity, Long> {
  Optional<FileRecordEntity> findByAllowedRootAndRelativePath(
      AllowedRootEntity allowedRoot, String relativePath);

  List<FileRecordEntity> findAllByAllowedRootOrderByRelativePathAsc(AllowedRootEntity allowedRoot);
}
