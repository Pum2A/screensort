package pl.pum.screensort.scanning.hashing;

import pl.pum.screensort.files.Sha256;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FileHasher {
  private static final Logger log = LoggerFactory.getLogger(FileHasher.class);

  public Optional<String> hash(Path path) {
    try (InputStream input = Files.newInputStream(path)) {
      return Optional.of(Sha256.hex(input));
    } catch (IOException e) {
      // Typ wyjątku zamiast komunikatu: komunikaty IO zawierają ścieżkę pliku.
      log.debug("File hashing failed: {}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }
}
