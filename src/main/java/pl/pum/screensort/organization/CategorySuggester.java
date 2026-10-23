package pl.pum.screensort.organization;

import pl.pum.screensort.organization.review.ImageCategory;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import pl.pum.screensort.analysis.result.AnalysisResult;

/** Explicit baseline, not a trained classifier. Every result still requires review. */
@Component
public class CategorySuggester {
  // Keyed by the category enum, so a suggestion can only ever name a real category.
  private static final Map<ImageCategory, Set<String>> RULES = Map.of(
      ImageCategory.PROGRAMMING, Set.of("code", "programming", "terminal", "exception", "postgresql", "java", "python"),
      ImageCategory.DOCUMENTS, Set.of("document", "invoice", "receipt", "spreadsheet", "paragon", "faktura"),
      ImageCategory.GAMES, Set.of("game", "gaming", "roblox", "minecraft", "videogame"),
      ImageCategory.PHOTOGRAPHS, Set.of("photograph", "landscape", "mountain", "beach"));

  public String suggest(AnalysisResult result) {
    String caption = result.visual().result() == null ? "" : result.visual().result().description();
    String text = result.ocr().result() == null ? "" : result.ocr().result().text();
    Set<String> words = Set.copyOf(java.util.Arrays.asList((caption + " " + text)
        .toLowerCase(Locale.ROOT).split("[^\\p{L}0-9]+")));
    var matches = new LinkedHashSet<ImageCategory>();
    RULES.forEach((category, keywords) -> {
      if (keywords.stream().anyMatch(words::contains)) matches.add(category);
    });
    return matches.size() == 1 ? matches.iterator().next().name() : "REVIEW";
  }
}
