package pl.pum.screensort.organization.review;

import java.util.Optional;

/**
 * Stable IDs, not user-supplied destination paths. REVIEW is a suggestion, not a final category.
 * The folder name is where an export puts the category; the frontend shows the same labels.
 */
public enum ImageCategory {
  PROGRAMMING("Programowanie"),
  DOCUMENTS("Dokumenty"),
  GAMES("Gry"),
  PHOTOGRAPHS("Fotografie"),
  OTHER("Pozostałe");

  private final String folder;

  ImageCategory(String folder) { this.folder = folder; }

  public String folder() { return folder; }

  public static Optional<ImageCategory> parse(String value) {
    for (var category : values()) if (category.name().equals(value)) return Optional.of(category);
    return Optional.empty();
  }

  public static boolean accepts(String value) {
    return parse(value).isPresent();
  }
}
