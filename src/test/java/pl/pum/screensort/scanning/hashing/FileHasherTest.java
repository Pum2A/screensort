package pl.pum.screensort.scanning.hashing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FileHasherTest {
  @Test
  void hashesExistingFile() {
    FileHasher hasher = new FileHasher();

    Optional<String> result = hasher.hash(Path.of("demo-images/xzzz1.png"));

    assertTrue(result.isPresent());
    assertEquals(64, result.get().length());
  }
}
