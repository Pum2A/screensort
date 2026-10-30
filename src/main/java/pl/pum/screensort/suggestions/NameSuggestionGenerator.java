package pl.pum.screensort.suggestions;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.pum.screensort.analysis.AnalysisResult;
import pl.pum.screensort.files.FileNames;

/**
 * A small, deterministic rule, not a language model. Up to two keywords from the text on the image
 * lead the name (a game title, an app name), and the visual caption fills the rest. The caption is
 * English; for Polish names its words go through {@link CaptionDictionary} and words without a
 * translation are left out, so one name never mixes the two languages. A proposal built from weak
 * evidence is marked uncertain so the owner decides.
 */
@Component
public class NameSuggestionGenerator {
  private static final Set<String> STOP_WORDS = Set.of(
      "a", "an", "the", "image", "picture", "photo", "photograph", "of", "with", "and",
      "on", "in", "at", "next", "to", "is", "are", "this", "there", "shows", "showing",
      "displaying", "contains", "visible", "can", "be", "seen", "some", "it", "its", "as",
      "from", "view", "obraz", "zdjecie", "przedstawia", "na", "jest", "oraz", "sie");
  /** Words that appear on almost any screenshot and say nothing about this one. */
  private static final Set<String> OCR_NOISE = Set.of(
      "for", "you", "your", "not", "was", "but", "all", "has", "have", "will",
      "http", "https", "www", "com", "org", "net", "html", "file", "edit", "help",
      "close", "cancel", "menu", "settings", "search", "home", "back", "more",
      "nie", "dla", "jak", "lub", "czy", "tak", "ale", "ten", "tym",
      "plik", "edycja", "widok", "pomoc", "zamknij", "anuluj", "ustawienia", "szukaj", "dalej");
  /** Caption words the vision model uses for nearly every screenshot. */
  private static final Set<String> GENERIC = Set.of(
      "screen", "screenshot", "text", "computer", "monitor", "display", "page", "website",
      "white", "black", "background", "close", "up", "blurry", "window", "words", "writing");
  private static final Pattern WORD = Pattern.compile("[a-z0-9]+");
  private static final Pattern RAW_WORD = Pattern.compile("[\\p{L}\\p{N}]+");
  private static final int MAX_WORDS = 6;
  private static final int MAX_KEYWORDS = 2;
  /** OCR of a full document page can be long; titles and app names are near the top. */
  private static final int OCR_SCAN_LIMIT = 4_000;

  private final Supplier<NamingLanguage> language;

  @Autowired
  public NameSuggestionGenerator(NamingSettings settings) { this(settings::language); }

  NameSuggestionGenerator(Supplier<NamingLanguage> language) { this.language = language; }

  /**
   * A model that named the app and topic gives the name directly ({@code discord_planowanie_wyjazdu});
   * otherwise the rule below builds it from the OCR text and the caption.
   */
  public Optional<NameSuggestion> suggest(String originalName, AnalysisResult result) {
    var visual = result.visual().status() == AnalysisResult.Status.COMPLETED ? result.visual().result() : null;
    if (visual != null && visual.subject() != null) return fromSubject(originalName, visual.subject());
    var ocr = result.ocr().status() == AnalysisResult.Status.COMPLETED ? result.ocr().result().text() : null;
    return suggest(originalName, ocr, visual == null ? null : visual.description());
  }

  private Optional<NameSuggestion> fromSubject(String originalName, AnalysisResult.Subject subject) {
    boolean polish = language.get() == NamingLanguage.PL;
    var words = new ArrayList<String>();
    for (String part : List.of(subject.app(), polish ? subject.topicPl() : subject.topicEn())) {
      var matcher = WORD.matcher(normalize(part));
      while (matcher.find() && words.size() < MAX_WORDS) {
        String word = matcher.group();
        if (!words.contains(word)) words.add(word.substring(0, Math.min(20, word.length())));
      }
    }
    if (words.isEmpty()) return Optional.empty();
    String name = String.join("_", words) + FileNames.extension(originalName);
    if (FileNameRules.problem(name, originalName).isPresent()) name = (polish ? "obraz_" : "image_") + name;
    if (FileNameRules.problem(name, originalName).isPresent()) return Optional.empty();
    return Optional.of(new NameSuggestion(name, words.size() < 2));
  }

  public Optional<NameSuggestion> suggest(String originalName, String ocrText, String description) {
    boolean polish = language.get() == NamingLanguage.PL;
    var keywords = keywords(ocrText);
    var words = new ArrayList<String>();
    for (Keyword keyword : keywords) words.add(keyword.word());
    boolean specificCaption = false;
    for (String caption : captionWords(description)) {
      if (words.size() >= MAX_WORDS) break;
      String word = polish ? CaptionDictionary.polish(caption).orElse(null) : caption;
      if (word == null || words.contains(word)) continue;
      words.add(word);
      specificCaption |= !GENERIC.contains(caption);
    }
    if (words.isEmpty()) return Optional.empty();
    String name = String.join("_", words) + FileNames.extension(originalName);
    if (FileNameRules.problem(name, originalName).isPresent()) name = (polish ? "obraz_" : "image_") + name;
    if (FileNameRules.problem(name, originalName).isPresent()) return Optional.empty();
    // Two words from somewhere specific are enough; a lone word or only filler words are not.
    boolean uncertain = words.size() < 2 || keywords.isEmpty() && !specificCaption;
    return Optional.of(new NameSuggestion(name, uncertain));
  }

  private static List<String> captionWords(String description) {
    var words = new ArrayList<String>();
    if (description == null || description.isBlank()) return words;
    var matcher = WORD.matcher(normalize(description));
    while (matcher.find() && words.size() < MAX_WORDS) {
      String word = matcher.group();
      if (!STOP_WORDS.contains(word)) words.add(word.substring(0, Math.min(20, word.length())));
    }
    return words;
  }

  private record Keyword(String word, int score, int position) {}

  /**
   * Scores OCR words: written in capitals (+2) or capitalised (+1), in the first three lines (+1),
   * and +1 for each repeat. Only words of 3-20 letters that are not noise or plain numbers count,
   * and only those scoring at least 2, so a stray lowercase word never leads the name.
   */
  private static List<Keyword> keywords(String ocrText) {
    if (ocrText == null || ocrText.isBlank()) return List.of();
    String text = ocrText.length() > OCR_SCAN_LIMIT ? ocrText.substring(0, OCR_SCAN_LIMIT) : ocrText;
    var scores = new LinkedHashMap<String, Integer>();
    int line = 0;
    for (String row : text.split("\\R")) {
      if (row.isBlank()) continue;
      var matcher = RAW_WORD.matcher(row);
      while (matcher.find()) {
        String raw = matcher.group();
        String word = normalize(raw);
        if (word.length() < 3 || word.length() > 20 || !WORD.matcher(word).matches()
            || word.chars().noneMatch(Character::isLetter) || STOP_WORDS.contains(word)
            || OCR_NOISE.contains(word)) continue;
        if (scores.containsKey(word)) {
          scores.merge(word, 1, Integer::sum);
          continue;
        }
        int score = line < 3 ? 1 : 0;
        if (!raw.equals(raw.toLowerCase(Locale.ROOT)) && raw.equals(raw.toUpperCase(Locale.ROOT))) score += 2;
        else if (Character.isUpperCase(raw.codePointAt(0))) score += 1;
        scores.put(word, score);
      }
      line++;
    }
    var order = List.copyOf(scores.keySet());
    return scores.entrySet().stream()
        .filter(entry -> entry.getValue() >= 2)
        .map(entry -> new Keyword(entry.getKey(), entry.getValue(), order.indexOf(entry.getKey())))
        .sorted(Comparator.comparingInt(Keyword::score).reversed().thenComparingInt(Keyword::position))
        .limit(MAX_KEYWORDS)
        // The best two, but in reading order: "Faktura VAT" stays faktura_vat.
        .sorted(Comparator.comparingInt(Keyword::position))
        .toList();
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replace('ł', 'l');
  }
}
