package pl.pum.screensort.naming;

import pl.pum.screensort.files.FileNames;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** A small, deterministic caption-to-name rule, not a language model or translation. */
@Component
public class NameSuggestionGenerator {
  private static final Set<String> STOP_WORDS = Set.of(
      "a", "an", "the", "image", "picture", "photo", "photograph", "of", "with", "and",
      "on", "in", "at", "next", "to", "is", "are", "this", "there", "shows", "showing",
      "displaying", "contains", "visible", "can", "be", "seen", "some", "it", "its", "as",
      "from", "view", "obraz", "zdjecie", "przedstawia", "na", "jest", "oraz", "sie");
  private static final Pattern WORD = Pattern.compile("[a-z0-9]+");
  private static final int MAX_WORDS = 6;

  public Optional<String> suggest(String originalName, String description) {
    if (description == null || description.isBlank()) return Optional.empty();
    String normalized = Normalizer.normalize(description, Normalizer.Form.NFKD)
        .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replace('ł', 'l');
    var words = new ArrayList<String>();
    var matcher = WORD.matcher(normalized);
    while (matcher.find() && words.size() < MAX_WORDS) {
      String word = matcher.group();
      if (!STOP_WORDS.contains(word)) words.add(word.substring(0, Math.min(20, word.length())));
    }
    if (words.isEmpty()) return Optional.empty();
    String stem = String.join("_", words);
    String extension = FileNames.extension(originalName);
    String name = stem + extension;
    if (FileNameRules.problem(name, originalName).isPresent()) name = "obraz_" + name;
    return FileNameRules.problem(name, originalName).isEmpty() ? Optional.of(name) : Optional.empty();
  }
}
