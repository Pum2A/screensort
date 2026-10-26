package pl.pum.screensort.naming;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameSuggestionGeneratorTest {
  private final NameSuggestionGenerator generator = new NameSuggestionGenerator(() -> NamingLanguage.EN);

  @Test
  void extractsAtMostSixCaptionWordsWithoutRunningAnAiModel() {
    assertThat(generator.suggest("IMG_0026.png", null, "A mug on a desk next to a computer monitor."))
        .contains(new NameSuggestion("mug_desk_computer_monitor.png", false));
    assertThat(generator.suggest("a.JPG", "", "one two three four five six seven"))
        .map(NameSuggestion::name).contains("one_two_three_four_five_six.JPG");
  }

  @Test
  void textOnTheImageLeadsTheNameAheadOfTheCaption() {
    // The reported case: "Roblox" was on the screenshot but never reached the name.
    assertThat(generator.suggest("Screenshot_141809.png", "ROBLOX\nLobby", "A cartoon character with a square head."))
        .contains(new NameSuggestion("roblox_lobby_cartoon_character_square_head.png", false));
    assertThat(generator.suggest("a.png", "Faktura VAT nr 12/2026\nSprzedawca: Firma", "A sheet of paper with text on it."))
        .map(NameSuggestion::name).contains("faktura_vat_sheet_paper_text.png");
  }

  @Test
  void ignoresNoiseNumbersAndWordsThatAreNotProminent() {
    String ocr = "File Edit View Help\nhttps://www.example.com\n2026 12 31\nsome lowercase body text";
    assertThat(generator.suggest("a.png", ocr, "A mug on a desk."))
        .contains(new NameSuggestion("mug_desk.png", false));
  }

  @Test
  void repeatedWordsFurtherDownStillCount() {
    String ocr = "\n\n\nnotes\nkotlin basics\nkotlin coroutines\nkotlin flows";
    assertThat(generator.suggest("a.png", ocr, "A sheet of paper."))
        .map(NameSuggestion::name).contains("kotlin_sheet_paper.png");
  }

  @Test
  void marksProposalsBuiltFromWeakEvidenceAsUncertain() {
    assertThat(generator.suggest("a.png", null, "A computer screen with text."))
        .contains(new NameSuggestion("computer_screen_text.png", true));
    assertThat(generator.suggest("a.png", "ROBLOX", null))
        .contains(new NameSuggestion("roblox.png", true));
    assertThat(generator.suggest("a.png", "JAVA", "A computer screen with text."))
        .contains(new NameSuggestion("java_computer_screen_text.png", false));
  }

  @Test
  void removesPunctuationAccentsAndReservedDeviceNames() {
    assertThat(generator.suggest("a.png", null, "Zdjęcie przedstawia: żółty kubek na biurku!"))
        .map(NameSuggestion::name).contains("zolty_kubek_biurku.png");
    assertThat(generator.suggest("a.png", "ŁÓDŹ", "Mapa miasta"))
        .map(NameSuggestion::name).contains("lodz_mapa_miasta.png");
    assertThat(generator.suggest("a.png", null, "CON")).contains(new NameSuggestion("image_con.png", true));
    String name = generator.suggest("a.png", "../../CON <SCRIPT>", "../../<script> bad:path | ?").orElseThrow().name();
    assertThat(FileNameRules.problem(name, "a.png")).isEmpty();
  }

  @Test
  void reportsInsufficientTextRatherThanInventingANameOrExtension() {
    assertThat(generator.suggest("a.png", null, null)).isEmpty();
    assertThat(generator.suggest("a.png", "ok 12", "The image is a picture of the photo")).isEmpty();
    assertThat(generator.suggest("no-extension", null, "A mug")).isEmpty();
  }

  @Test
  void polishNamesTranslateTheCaptionAndNeverMixInEnglishWords() {
    var polish = new NameSuggestionGenerator(() -> NamingLanguage.PL);
    assertThat(polish.suggest("IMG_0026.png", null, "A mug on a desk next to a computer monitor."))
        .contains(new NameSuggestion("kubek_biurko_komputer_monitor.png", false));
    // Text from the screenshot stays as written; caption words without a Polish entry are left out.
    assertThat(polish.suggest("a.png", "ROBLOX\nLobby", "A cartoon character with a square head."))
        .map(NameSuggestion::name).contains("roblox_lobby_kreskowka_postac.png");
    assertThat(polish.suggest("a.png", null, "CON")).isEmpty();
    assertThat(polish.suggest("a.png", null, "A screenshot of a computer screen."))
        .contains(new NameSuggestion("zrzut_komputer_ekran.png", true));
  }
}
