package pl.pum.screensort.gallery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.pum.screensort.gallery.reading.AllowedRoot;
import pl.pum.screensort.gallery.reading.KnownFile;

@Component
public class KnownFilesReader {
  // CEL: przygotować poprzednie opisy plików z bazy dla jednego skanowania.
  // Ta klasa nie otwiera zdjęć i nie podejmuje decyzji accepted/rejected.
  //
  private final AllowedRootRepository allowedRootRepository;
  private final FileRecordRepository fileRecordRepository;

  public KnownFilesReader(
      AllowedRootRepository allowedRootRepository, FileRecordRepository fileRecordRepository) {
    this.allowedRootRepository = allowedRootRepository;
    this.fileRecordRepository = fileRecordRepository;
  }

  @Transactional(readOnly = true)
  public Map<String, KnownFile> read(AllowedRoot allowedRoot) {
    String folderPath = allowedRoot.path().toString();
    Optional<AllowedRootEntity> existingRoot =
        allowedRootRepository.findByCanonicalPath(folderPath);

    List<KnownFile> knownFiles =
        existingRoot.isEmpty()
            ? List.of()
            : fileRecordRepository.findAllByAllowedRoot_Id(existingRoot.get().getId());

    Map<String, KnownFile> knowFilesByPath = new HashMap<>();

    for (KnownFile file : knownFiles) {
      knowFilesByPath.put(file.relativePath(), file);
    }
    return Map.copyOf(knowFilesByPath);
  }

}
