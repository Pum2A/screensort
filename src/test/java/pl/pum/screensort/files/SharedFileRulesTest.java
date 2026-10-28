package pl.pum.screensort.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pl.pum.screensort.review.ImageCategory;

class SharedFileRulesTest {
  private static final String ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

  @Test void everySha256FormGivesTheSameLowercaseHex() throws Exception {
    byte[] abc = "abc".getBytes(StandardCharsets.US_ASCII);
    assertThat(Sha256.hex(abc)).isEqualTo(ABC);
    assertThat(Sha256.hex(new ByteArrayInputStream(abc))).isEqualTo(ABC);
    var digest = Sha256.digest();
    digest.update(abc);
    assertThat(Sha256.hex(digest)).isEqualTo(ABC);
  }

  @Test void aPlainNameInsideTheRootIsAccepted() {
    Path root = Path.of("C:/Pictures/Screens").toAbsolutePath();
    assertThat(SourcePath.directChild(root, "shot 1.png")).contains(root.resolve("shot 1.png"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", ".", "..", "../outside.png", "nested/photo.png", "photo.png:secret"})
  void anythingButAPlainNameIsRejected(String relative) {
    Path root = Path.of("C:/Pictures/Screens").toAbsolutePath();
    assertThat(SourcePath.directChild(root, relative)).isEmpty();
    assertThat(SourcePath.directChild(root, null)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"photo.PNG, .PNG, true", "shot.jpeg, .jpeg, true", "archive.tar.gz, .gz, false",
      ".hidden, '', false", "noext, '', false", "name., '', false"})
  void extensionsFollowOneRule(String name, String extension, boolean image) {
    assertThat(FileNames.extension(name)).isEqualTo(extension);
    assertThat(ImageFormats.hasImageExtension(name)).isEqualTo(image);
  }

  @Test void readableFormatsIgnoreCase() {
    assertThat(ImageFormats.readable("JPEG")).isTrue();
    assertThat(ImageFormats.readable("gif")).isFalse();
  }

  @Test void eachCategoryHasItsExportFolder() {
    assertThat(ImageCategory.parse("PHOTOGRAPHS")).map(ImageCategory::folder).contains("Fotografie");
    assertThat(ImageCategory.parse("REVIEW")).isEmpty();
    assertThat(ImageCategory.accepts("OTHER")).isTrue();
  }
}
