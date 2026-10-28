package pl.pum.screensort.analysis;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileAnalysisRepository extends JpaRepository<FileAnalysisEntity, Long> {
  Optional<FileAnalysisEntity> findByFile_IdAndOcrEnabledAndVisualEnabled(long id, boolean ocr, boolean visual);
  Optional<FileAnalysisEntity> findFirstByFile_IdOrderByAnalyzedAtDesc(long id);
}
