package pl.pum.screensort.gallery.reading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileHasherTest {
  @TempDir Path temporaryDirectory;

  @Test
  void hashesExistingFile() {
    FileHasher hasher = new FileHasher();

    Optional<String> result = hasher.hash(Path.of("demo-images/xzzz1.png"));

    assertTrue(result.isPresent());
    assertEquals(64, result.get().length());
    assertEquals(
        "d095274923ae2ae713da857aead939d4878c362d9bad9d39a6258c5c9607bd8d",
        result.get());
  }

  @Test
  void matchesKnownSha256ForAbc() throws Exception {
    Path file = temporaryDirectory.resolve("abc.txt");
    Files.writeString(file, "abc");

    assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        new FileHasher().hash(file).orElseThrow());
  }

  @Test
  void hashesEmptyFile() throws Exception {
    Path file = Files.createFile(temporaryDirectory.resolve("empty.bin"));

    assertEquals(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        new FileHasher().hash(file).orElseThrow());
  }

  @ParameterizedTest
  @ValueSource(ints = {65_535, 65_536, 65_537, 196_745})
  void matchesWholeFileHashAroundBufferBoundaries(int sizeBytes) throws Exception {
    byte[] content = new byte[sizeBytes];
    for (int index = 0; index < content.length; index++) {
      content[index] = (byte) (index * 31 + 7);
    }
    Path file = temporaryDirectory.resolve("boundary.bin");
    Files.write(file, content);
    String expectedHash =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));

    assertEquals(expectedHash, new FileHasher().hash(file).orElseThrow());
  }

  @Test
  void keepsDigestStateSeparateBetweenCalls() throws Exception {
    Path firstFile = temporaryDirectory.resolve("first.txt");
    Path secondFile = temporaryDirectory.resolve("second.txt");
    Files.writeString(firstFile, "abc");
    Files.createFile(secondFile);
    FileHasher hasher = new FileHasher();

    assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        hasher.hash(firstFile).orElseThrow());
    assertEquals(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        hasher.hash(secondFile).orElseThrow());
  }

  @Test
  void returnsEmptyWhenFileDoesNotExist() {
    Path missingFile = temporaryDirectory.resolve("missing.bin");

    assertTrue(new FileHasher().hash(missingFile).isEmpty());
  }
}
