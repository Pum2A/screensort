package pl.pum.screensort.naming;

/** A proposed filename; {@code uncertain} asks the owner to check it before approving. */
public record NameSuggestion(String name, boolean uncertain) {}
