package pl.pum.screensort.scanning.hashing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

public class FileHasher {

  public Optional<String> hash(Path path) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = Files.readAllBytes(path);
      byte[] hashed = digest.digest(bytes);
      String hex = HexFormat.of().formatHex(hashed);
      return Optional.of(hex); // udało się → pudełko z hashem
    } catch (IOException | NoSuchAlgorithmException e) {
      return Optional.empty(); // błąd → puste pudełko
    }
  }
}
