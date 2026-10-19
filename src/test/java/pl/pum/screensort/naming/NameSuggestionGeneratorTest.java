package pl.pum.screensort.naming;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameSuggestionGeneratorTest {
  private final NameSuggestionGenerator generator = new NameSuggestionGenerator();

  @Test
  void extractsAtMostSixCaptionWordsWithoutRunningAnAiModel() {
    assertThat(generator.suggest("IMG_0026.png", "A mug on a desk next to a computer monitor."))
        .contains("mug_desk_computer_monitor.png");
    assertThat(generator.suggest("a.JPG", "one two three four five six seven"))
        .contains("one_two_three_four_five_six.JPG");
  }

  @Test
  void removesPunctuationAccentsAndReservedDeviceNames() {
    assertThat(generator.suggest("a.png", "Zdjęcie przedstawia: żółty kubek na biurku!"))
        .contains("zolty_kubek_biurku.png");
    assertThat(generator.suggest("a.png", "CON")).contains("obraz_con.png");
    String name = generator.suggest("a.png", "../../<script> bad:path | ?").orElseThrow();
    assertThat(FileNameRules.problem(name, "a.png")).isEmpty();
  }

  @Test
  void reportsInsufficientTextRatherThanInventingANameOrExtension() {
    assertThat(generator.suggest("a.png", null)).isEmpty();
    assertThat(generator.suggest("a.png", "The image is a picture of the photo")).isEmpty();
    assertThat(generator.suggest("no-extension", "A mug")).isEmpty();
  }
}
