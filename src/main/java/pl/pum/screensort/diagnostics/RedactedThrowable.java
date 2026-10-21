package pl.pum.screensort.diagnostics;

/**
 * Log-safe copy of an exception: keeps types, stack frames, causes and suppressed exceptions,
 * drops every message. File IO messages (NoSuchFileException, AccessDeniedException, ...) carry
 * absolute paths, which must never reach the log.
 */
public final class RedactedThrowable extends RuntimeException {
  private static final int MAX_DEPTH = 16;

  private RedactedThrowable(Throwable original, int depth) {
    // The message is the original type, so log output still says what failed.
    super(original.getClass().getName(), redactedCause(original, depth), true, true);
    setStackTrace(original.getStackTrace());
    if (depth < MAX_DEPTH) {
      for (Throwable suppressed : original.getSuppressed()) {
        addSuppressed(new RedactedThrowable(suppressed, depth + 1));
      }
    }
  }

  public static RedactedThrowable of(Throwable original) {
    return new RedactedThrowable(original, 0);
  }

  private static RedactedThrowable redactedCause(Throwable original, int depth) {
    Throwable cause = original.getCause();
    // The depth limit also stops cyclic cause chains.
    return cause == null || cause == original || depth >= MAX_DEPTH
        ? null
        : new RedactedThrowable(cause, depth + 1);
  }
}
