package pl.pum.screensort.suggestions;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileNamingRepository extends JpaRepository<FileNamingEntity, Long> {
  Optional<FileNamingEntity> findByFile_Id(long fileId);
}
