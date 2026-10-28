package pl.pum.screensort.gallery.preview;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.pum.screensort.diagnostics.ProductMetrics;

class DiskPreviewCacheTest {
  @TempDir Path directory;
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @Test void previewSurvivesAnAppRestartUnderAHashedName() throws IOException {
    cache(1024).put(key('a'), new byte[] {1, 2, 3});

    assertThat(cache(1024).get(key('a'))).containsExactly((byte) 1, (byte) 2, (byte) 3);
    try (var files = Files.list(directory)) {
      assertThat(files.map(path -> path.getFileName().toString()))
          .containsExactly("v1-" + "a".repeat(64) + ".png");
    }
    assertThat(count("disk", "hit")).isEqualTo(1);
  }

  @Test void unknownKeyIsAMiss() {
    assertThat(cache(1024).get(key('b'))).isNull();
    assertThat(count("disk", "miss")).isEqualTo(1);
  }

  @Test void ignoresKeysThatAreNotPreviewKeys() throws IOException {
    var cache = cache(1024);
    cache.put("../outside", new byte[] {1});
    cache.put("screensort:preview:v1:not-a-hash", new byte[] {1});

    assertThat(cache.get("../outside")).isNull();
    try (var files = Files.list(directory)) {
      assertThat(files).isEmpty();
    }
  }

  @Test void overTheLimitRemovesTheLeastRecentlyReadPreviewFirst() throws IOException {
    var cache = cache(1000);
    cache.put(key('a'), new byte[400]);
    cache.put(key('b'), new byte[400]);
    Files.setLastModifiedTime(file('a'), FileTime.from(Instant.now().minusSeconds(20)));
    Files.setLastModifiedTime(file('b'), FileTime.from(Instant.now().minusSeconds(10)));
    // Reading "a" makes it the most recently used, so "b" is now the oldest.
    assertThat(cache.get(key('a'))).hasSize(400);

    cache.put(key('c'), new byte[400]);

    assertThat(file('a')).exists();
    assertThat(file('b')).doesNotExist();
    assertThat(file('c')).exists();
  }

  @Test void startupRemovesUnfinishedWritesAndTrimsToTheLimit() throws IOException {
    Files.write(directory.resolve("preview-123.tmp"), new byte[] {1});
    Files.write(file('a'), new byte[600]);
    Files.setLastModifiedTime(file('a'), FileTime.from(Instant.now().minusSeconds(10)));
    Files.write(file('b'), new byte[600]);

    cache(1000);

    assertThat(directory.resolve("preview-123.tmp")).doesNotExist();
    assertThat(file('a')).doesNotExist();
    assertThat(file('b')).exists();
  }

  @Test void skipsPreviewsLargerThanTheEntryLimit() {
    var cache = cache(64L * 1024 * 1024);
    cache.put(key('a'), new byte[DiskPreviewCache.MAX_ENTRY_BYTES + 1]);

    assertThat(cache.get(key('a'))).isNull();
  }

  private DiskPreviewCache cache(long maxBytes) {
    return new DiskPreviewCache(directory, maxBytes, new ProductMetrics(registry));
  }

  private Path file(char hash) {
    return directory.resolve("v1-" + String.valueOf(hash).repeat(64) + ".png");
  }

  private static String key(char hash) {
    return "screensort:preview:v1:" + String.valueOf(hash).repeat(64);
  }

  private double count(String layer, String outcome) {
    var counter = registry.find("screensort.preview.cache").tag("layer", layer).tag("outcome", outcome).counter();
    return counter == null ? 0 : counter.count();
  }
}
