package pl.pum.screensort.suggestions;

/** A proposed filename; {@code uncertain} asks the owner to check it before approving. */
public record NameSuggestion(String name, boolean uncertain) {}
