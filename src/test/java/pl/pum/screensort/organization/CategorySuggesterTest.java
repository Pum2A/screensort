package pl.pum.screensort.organization;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import pl.pum.screensort.analysis.result.AnalysisResult;

class CategorySuggesterTest {
  final CategorySuggester categories = new CategorySuggester();

  @Test void doesNotInventRobloxFromAGenericCartoonCaption() {
    assertThat(categories.suggest(result("A cartoon character", ""))).isEqualTo("REVIEW");
  }
  @Test void matchesWholeWordsAndRequiresReviewForConflictingCategories() {
    assertThat(categories.suggest(result("A game avatar", ""))).isEqualTo("GAMES");
    assertThat(categories.suggest(result("A terminal", "invoice"))).isEqualTo("REVIEW");
    assertThat(categories.suggest(result("A gamer", ""))).isEqualTo("REVIEW");
  }
  @Test void canUseOcrButDoesNotTreatEmptyResultsAsRecognizedContent() {
    assertThat(categories.suggest(result("", "Faktura VAT"))).isEqualTo("DOCUMENTS");
    assertThat(categories.suggest(result("", ""))).isEqualTo("REVIEW");
  }
  private AnalysisResult result(String caption, String text) {
    return new AnalysisResult("a".repeat(64),
        new AnalysisResult.OcrStep(AnalysisResult.Status.COMPLETED, new AnalysisResult.Ocr(text, "test", "1", 1), null),
        caption.isEmpty() ? new AnalysisResult.VisualStep(AnalysisResult.Status.SKIPPED, null, null)
            : new AnalysisResult.VisualStep(AnalysisResult.Status.COMPLETED,
                new AnalysisResult.Visual(caption, "test", "1", "caption", "cpu", 1), null));
  }
}
