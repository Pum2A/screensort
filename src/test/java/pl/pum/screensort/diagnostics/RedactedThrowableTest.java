package pl.pum.screensort.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import org.junit.jupiter.api.Test;

class RedactedThrowableTest {
  private static final String PRIVATE_PATH = "C:\\Users\\owner\\Pictures\\private-screenshot.png";

  @Test
  void keepsTypesAndFramesButDropsMessages() {
    var io = new NoSuchFileException(PRIVATE_PATH);
    io.addSuppressed(new AccessDeniedException(PRIVATE_PATH));
    var original = new UncheckedIOException("Copy failed for " + PRIVATE_PATH, io);

    RedactedThrowable redacted = RedactedThrowable.of(original);

    assertThat(redacted.getMessage()).isEqualTo(UncheckedIOException.class.getName());
    assertThat(redacted.getStackTrace()).isEqualTo(original.getStackTrace());
    assertThat(redacted.getCause().getMessage()).isEqualTo(NoSuchFileException.class.getName());
    assertThat(redacted.getCause().getSuppressed()).singleElement()
        .extracting(Throwable::getMessage).isEqualTo(AccessDeniedException.class.getName());
    assertThat(printed(redacted)).doesNotContain("private-screenshot").doesNotContain("owner");
  }

  @Test
  void survivesCyclicCauseChain() {
    var first = new IllegalStateException(PRIVATE_PATH);
    var second = new IllegalArgumentException(PRIVATE_PATH, first);
    first.initCause(second);

    assertThat(printed(RedactedThrowable.of(first))).doesNotContain("private-screenshot");
  }

  private static String printed(Throwable throwable) {
    var out = new StringWriter();
    throwable.printStackTrace(new PrintWriter(out));
    return out.toString();
  }
}
