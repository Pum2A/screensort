package pl.pum.screensort.suggestions;

import pl.pum.screensort.analysis.AnalysisResult;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

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
  @Test void theModelCategoryWinsAndUnknownMeansReview() {
    assertThat(categories.suggest(model("Programowanie", "invoice game"))).isEqualTo("PROGRAMMING");
    assertThat(categories.suggest(model("Zdjęcia", "x"))).isEqualTo("PHOTOGRAPHS");
    assertThat(categories.suggest(model("Czaty", "x"))).isEqualTo("OTHER");
    assertThat(categories.suggest(model("Inne", "x"))).isEqualTo("REVIEW");
  }
  static AnalysisResult model(String category, String topicEn) {
    var subject = new AnalysisResult.Subject("App", "temat", topicEn, category);
    return new AnalysisResult("a".repeat(64), new AnalysisResult.OcrStep(AnalysisResult.Status.SKIPPED, null, null),
        new AnalysisResult.VisualStep(AnalysisResult.Status.COMPLETED,
            new AnalysisResult.Visual("App: " + topicEn, "qwen", "1", "screenshot", "gpu", 1, subject), null));
  }
  private AnalysisResult result(String caption, String text) {
    return new AnalysisResult("a".repeat(64),
        new AnalysisResult.OcrStep(AnalysisResult.Status.COMPLETED, new AnalysisResult.Ocr(text, "test", "1", 1), null),
        caption.isEmpty() ? new AnalysisResult.VisualStep(AnalysisResult.Status.SKIPPED, null, null)
            : new AnalysisResult.VisualStep(AnalysisResult.Status.COMPLETED,
                new AnalysisResult.Visual(caption, "test", "1", "caption", "cpu", 1), null));
  }
}
