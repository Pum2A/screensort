package pl.pum.screensort.gallery.preview;

import java.time.Duration;
import java.util.function.LongSupplier;
import org.springframework.data.redis.core.RedisTemplate;
import pl.pum.screensort.diagnostics.ProductMetrics;
import static pl.pum.screensort.diagnostics.ProductMetrics.CacheLayer.REDIS;
import static pl.pum.screensort.diagnostics.ProductMetrics.CacheOutcome.*;

/** Disposable encoded previews only. A failure opens a 30-second local backoff, never a DB failure. */
public class RedisPreviewCache {
  static final Duration TTL = Duration.ofMinutes(10);
  static final long BACKOFF_NANOS = Duration.ofSeconds(30).toNanos();
  static final int MAX_BYTES = 2 * 1024 * 1024;
  private final RedisTemplate<String, byte[]> redis;
  private final ProductMetrics metrics;
  private final LongSupplier clock;
  private volatile long failedAt;
  private volatile boolean backingOff;

  public RedisPreviewCache(RedisTemplate<String, byte[]> redis, ProductMetrics metrics) {
    this(redis, metrics, System::nanoTime);
  }

  RedisPreviewCache(RedisTemplate<String, byte[]> redis, ProductMetrics metrics, LongSupplier clock) {
    this.redis = redis; this.metrics = metrics; this.clock = clock;
  }

  public byte[] get(String key) {
    if (unavailable()) return null;
    try {
      byte[] bytes = redis.opsForValue().get(key);
      if (bytes != null && bytes.length > MAX_BYTES) {
        metrics.cache(REDIS, ERROR);
        return null;
      }
      metrics.cache(REDIS, bytes == null ? MISS : HIT);
      return bytes;
    } catch (RuntimeException ignored) {
      failed();
      return null;
    }
  }

  public void put(String key, byte[] bytes) {
    if (bytes.length > MAX_BYTES || unavailable()) return;
    try {
      redis.opsForValue().set(key, bytes, TTL);
    } catch (RuntimeException ignored) {
      failed();
    }
  }

  private boolean unavailable() {
    if (backingOff && clock.getAsLong() - failedAt < BACKOFF_NANOS) {
      metrics.cache(REDIS, BYPASS);
      return true;
    }
    return false;
  }

  private void failed() {
    failedAt = clock.getAsLong();
    backingOff = true;
    metrics.cache(REDIS, ERROR);
    // No exception dump: client diagnostics can contain addresses or application data.
  }
}
