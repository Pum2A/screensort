package pl.pum.screensort.organization.review;

/** Stable IDs, not user-supplied destination paths. REVIEW is a suggestion, not a final category. */
public enum ImageCategory {
  PROGRAMMING, DOCUMENTS, GAMES, PHOTOGRAPHS, OTHER;

  public static boolean accepts(String value) {
    for (var category : values()) if (category.name().equals(value)) return true;
    return false;
  }
}
