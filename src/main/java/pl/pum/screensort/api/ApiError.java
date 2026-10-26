package pl.pum.screensort.api;

/**
 * The one error body of the whole HTTP API. {@code code} is stable and meant for the frontend;
 * {@code message} is a ready Polish sentence and never holds paths, hashes or raw exception text.
 */
public record ApiError(String code, String message) {}
