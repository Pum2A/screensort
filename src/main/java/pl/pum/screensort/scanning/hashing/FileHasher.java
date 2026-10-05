package pl.pum.screensort.scanning.hashing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class FileHasher {
  private static final int HASH_BUFFER_SIZE_BYTES = 64 * 1024;

  public Optional<String> hash(Path path) {
    try (InputStream input = Files.newInputStream(path)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[HASH_BUFFER_SIZE_BYTES];
      int bytesRead;

      while ((bytesRead = input.read(buffer)) != -1) {
        digest.update(buffer, 0, bytesRead);
      }

      byte[] hashed = digest.digest();
      String hex = HexFormat.of().formatHex(hashed);
      return Optional.of(hex); // udało się → pudełko z hashem
    } catch (IOException | NoSuchAlgorithmException e) {
      return Optional.empty(); // błąd → puste pudełko
    }
  }
}
