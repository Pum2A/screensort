package pl.pum.screensort.catalog.database;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.pum.screensort.scanning.knownfiles.KnownFile;

public interface FileRecordRepository extends JpaRepository<FileRecordEntity, Long> {
  Optional<FileRecordEntity> findByAllowedRootAndRelativePath(
      AllowedRootEntity allowedRoot, String relativePath);

  List<FileRecordEntity> findAllByAllowedRootOrderByRelativePathAsc(AllowedRootEntity allowedRoot);

  Page<FileRecordEntity> findAllByAllowedRootOrderByRelativePathAsc(
      AllowedRootEntity allowedRoot, Pageable pageable);

  List<KnownFile> findAllByAllowedRoot_Id(Long rootId);
}
