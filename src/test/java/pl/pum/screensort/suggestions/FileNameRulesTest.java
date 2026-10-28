package pl.pum.screensort.suggestions;

import pl.pum.screensort.files.FileNames;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileNameRulesTest {
  @Test
  void acceptsUserNamesMultipleDotsAndExtensionCaseWithoutChangingInput() {
    for (String name : new String[]{"kubek_na_biurku.png", "Moje zdjęcie.PNG", "2042.123.421.png"}) {
      assertThat(FileNameRules.problem(name, "IMG_0026.png")).isEmpty();
    }
    assertThat(FileNames.extension("2042.123.421.png")).isEqualTo(".png");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "../photo.png", "C:\\photo.png", "a/b.png", "a?b.png",
      "a\nb.png", "a\u0000b.png", "a\u202eb.png", "CON.png", "con .png", "NUL.png",
      "COM1.png", "LPT9.png", "COM¹.png", "CONIN$.png", "CLOCK$.png", ".png", "..png",
      "photo.jpg", "photo.png ", "photo.png.", " photo.png"})
  void rejectsPathsReservedNamesControlCharactersAndExtensionChanges(String name) {
    assertThat(FileNameRules.problem(name, "original.png")).isPresent();
  }

  @Test
  void handlesNullAndBothCharacterAndUtf8ByteLimits() {
    assertThat(FileNameRules.problem(null, "original.png")).isPresent();
    assertThat(FileNameRules.problem("a".repeat(251) + ".png", "original.png")).isEmpty();
    assertThat(FileNameRules.problem("a".repeat(252) + ".png", "original.png")).isPresent();
    assertThat(FileNameRules.problem("ą".repeat(126) + ".png", "original.png")).isPresent();
  }
}
