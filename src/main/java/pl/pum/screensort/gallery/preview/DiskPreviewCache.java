package pl.pum.screensort.gallery.preview;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static pl.pum.screensort.metrics.ProductMetrics.CacheLayer.DISK;
import static pl.pum.screensort.metrics.ProductMetrics.CacheOutcome.ERROR;
import static pl.pum.screensort.metrics.ProductMetrics.CacheOutcome.HIT;
import static pl.pum.screensort.metrics.ProductMetrics.CacheOutcome.MISS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.pum.screensort.metrics.ProductMetrics;

/**
 * Encoded previews kept between app restarts. Disposable: a missing, broken or deleted file only
 * means one more decode. File names come from the hashed cache key, never from source names.
 */
public class DiskPreviewCache {
  static final int MAX_ENTRY_BYTES = 2 * 1024 * 1024;
  private static final Logger log = LoggerFactory.getLogger(DiskPreviewCache.class);
  private static final Pattern KEY = Pattern.compile("^screensort:preview:(v[0-9]+):([0-9a-f]{64})$");
  private static final String SUFFIX = ".png";
  private static final String TEMP_SUFFIX = ".tmp";
  private static final long TRIM_RETRY_NANOS = Duration.ofMinutes(1).toNanos();

  private final Path directory;
  private final long maxBytes;
  private final ProductMetrics metrics;
  private final boolean disabled;
  private long usedBytes;
  // nanoTime has no fixed origin, so "no wait" is "now", not zero.
  private long nextTrimAt = System.nanoTime();

  public DiskPreviewCache(Path directory, long maxBytes, ProductMetrics metrics) {
    this.directory = directory.toAbsolutePath().normalize();
    this.maxBytes = maxBytes;
    this.metrics = metrics;
    boolean ready = false;
    try {
      Files.createDirectories(this.directory);
      usedBytes = removeLeftoversAndMeasure();
      trim();
      ready = true;
    } catch (IOException | RuntimeException e) {
      // No exception text: IO messages contain the cache path, which includes the user name.
      log.warn("Preview disk cache disabled: {}", e.getClass().getSimpleName());
    }
    disabled = !ready;
  }

  public byte[] get(String key) {
    Path file = file(key);
    if (file == null) return null;
    try {
      if (Files.size(file) > MAX_ENTRY_BYTES) {
        metrics.cache(DISK, ERROR);
        return null;
      }
      byte[] bytes = Files.readAllBytes(file);
      // The modification time doubles as "last used", so trimming removes the oldest reads first.
      touch(file);
      metrics.cache(DISK, HIT);
      return bytes;
    } catch (NoSuchFileException e) {
      metrics.cache(DISK, MISS);
      return null;
    } catch (IOException e) {
      metrics.cache(DISK, ERROR);
      return null;
    }
  }

  public synchronized void put(String key, byte[] bytes) {
    Path file = file(key);
    if (file == null || bytes.length > MAX_ENTRY_BYTES) return;
    Path temp = null;
    try {
      long previous = Files.exists(file) ? Files.size(file) : 0;
      // Write aside, then rename: a crash leaves a stray .tmp, never a half-written preview.
      temp = Files.createTempFile(directory, "preview-", TEMP_SUFFIX);
      Files.write(temp, bytes);
      Files.move(temp, file, ATOMIC_MOVE, REPLACE_EXISTING);
      temp = null;
      usedBytes += bytes.length - previous;
      if (usedBytes > maxBytes && System.nanoTime() - nextTrimAt >= 0) trim();
    } catch (IOException e) {
      metrics.cache(DISK, ERROR);
    } finally {
      if (temp != null) deleteQuietly(temp);
    }
  }

  private Path file(String key) {
    if (disabled) return null;
    var match = KEY.matcher(key);
    if (!match.matches()) return null;
    return directory.resolve(match.group(1) + "-" + match.group(2) + SUFFIX);
  }

  private long removeLeftoversAndMeasure() throws IOException {
    long total = 0;
    try (Stream<Path> files = Files.list(directory)) {
      for (Path path : files.toList()) {
        String name = path.getFileName().toString();
        if (name.endsWith(TEMP_SUFFIX)) deleteQuietly(path);
        else if (name.endsWith(SUFFIX) && Files.isRegularFile(path)) total += Files.size(path);
      }
    }
    return total;
  }

  // Drops the least recently used previews until the cache is back under 90% of its limit.
  private synchronized void trim() throws IOException {
    if (usedBytes <= maxBytes) return;
    record Entry(Path path, long bytes, FileTime usedAt) {}
    List<Entry> entries = new ArrayList<>();
    try (Stream<Path> files = Files.list(directory)) {
      for (Path path : files.toList()) {
        if (!path.getFileName().toString().endsWith(SUFFIX)) continue;
        try {
          entries.add(new Entry(path, Files.size(path), Files.getLastModifiedTime(path)));
        } catch (NoSuchFileException ignored) {
          // Another request already replaced it; nothing to count.
        }
      }
    }
    entries.sort(Comparator.comparing(Entry::usedAt));
    usedBytes = entries.stream().mapToLong(Entry::bytes).sum();
    long target = maxBytes / 10 * 9;
    for (Entry entry : entries) {
      if (usedBytes <= target) break;
      if (deleteQuietly(entry.path())) usedBytes -= entry.bytes();
    }
    // Files another program keeps open cannot be deleted. Listing the folder on every write
    // would not help, so the next attempt waits a minute.
    if (usedBytes > maxBytes) nextTrimAt = System.nanoTime() + TRIM_RETRY_NANOS;
  }

  private static void touch(Path file) {
    try {
      Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
    } catch (IOException ignored) {
      // A stale timestamp only makes this preview an earlier candidate for trimming.
    }
  }

  private static boolean deleteQuietly(Path path) {
    try {
      return Files.deleteIfExists(path);
    } catch (IOException ignored) {
      return false;
    }
  }
}
