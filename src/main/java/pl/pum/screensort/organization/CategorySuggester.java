package pl.pum.screensort.organization;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import pl.pum.screensort.analysis.result.AnalysisResult;

/** Explicit baseline, not a trained classifier. Every result still requires review. */
@Component
public class CategorySuggester {
  private static final Map<String, Set<String>> RULES = Map.of(
      "PROGRAMMING", Set.of("code", "programming", "terminal", "exception", "postgresql", "java", "python"),
      "DOCUMENTS", Set.of("document", "invoice", "receipt", "spreadsheet", "paragon", "faktura"),
      "GAMES", Set.of("game", "gaming", "roblox", "minecraft", "videogame"),
      "PHOTOGRAPHS", Set.of("photograph", "landscape", "mountain", "beach"));

  public String suggest(AnalysisResult result) {
    String caption = result.visual().result() == null ? "" : result.visual().result().description();
    String text = result.ocr().result() == null ? "" : result.ocr().result().text();
    Set<String> words = Set.copyOf(java.util.Arrays.asList((caption + " " + text)
        .toLowerCase(Locale.ROOT).split("[^\\p{L}0-9]+")));
    var matches = new LinkedHashSet<String>();
    RULES.forEach((category, keywords) -> {
      if (keywords.stream().anyMatch(words::contains)) matches.add(category);
    });
    return matches.size() == 1 ? matches.iterator().next() : "REVIEW";
  }
}
