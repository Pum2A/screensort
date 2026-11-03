package pl.pum.screensort.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class TextSearchTest {
  private static TextSearch.Hit hit(String text, String needle) {
    return TextSearch.hit(7, TextSearch.Source.TEXT, text, needle.toLowerCase(Locale.forLanguageTag("pl")));
  }

  @Test void findsTheFirstMatchIgnoringCaseAndKeepsTheOriginalSpelling() {
    var found = hit("Sprzedawca: Firma\nFAKTURA VAT nr 12/2026\nRazem 120 zł", "faktura");
    assertThat(found.match()).isEqualTo("FAKTURA");
    assertThat(found.before()).isEqualTo("Sprzedawca: Firma ");
    assertThat(found.after()).startsWith(" VAT nr 12/2026 Razem");
  }

  @Test void polishLettersAndLongTextGiveAShortExcerptOnOneLine() {
    String text = "a".repeat(100) + " Zażółć gęślą jaźń " + "b".repeat(100);
    var found = hit(text, "GĘŚLĄ");
    assertThat(found.match()).isEqualTo("gęślą");
    assertThat(found.before()).startsWith("…").hasSizeLessThan(32);
    assertThat(found.after()).endsWith("…");
    assertThat(hit("Lobby", "faktura")).isNull();
  }
}
