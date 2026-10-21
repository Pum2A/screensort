package pl.pum.screensort.catalog.preview;

import java.util.Optional;
import org.springframework.stereotype.Component;
import pl.pum.screensort.catalog.preview.redis.RedisPreviewCache;
import pl.pum.screensort.diagnostics.ProductMetrics;
import static pl.pum.screensort.diagnostics.ProductMetrics.CacheLayer.MEMORY;
import static pl.pum.screensort.diagnostics.ProductMetrics.CacheOutcome.*;

/** Small in-process LRU first; optional short-lived Redis second. Neither is authoritative. */
@Component
public class PreviewStore {
  private final PreviewCache memory = new PreviewCache(128, 16L * 1024 * 1024);
  private final Optional<RedisPreviewCache> redis;
  private final ProductMetrics metrics;

  public PreviewStore(Optional<RedisPreviewCache> redis, ProductMetrics metrics) {
    this.redis = redis;
    this.metrics = metrics;
  }

  public byte[] get(String key) {
    byte[] bytes = memory.get(key);
    metrics.cache(MEMORY, bytes == null ? MISS : HIT);
    if (bytes == null && redis.isPresent()) {
      bytes = redis.get().get(key);
      if (bytes != null) memory.put(key, bytes);
    }
    return bytes;
  }

  public void put(String key, byte[] bytes) {
    memory.put(key, bytes);
    redis.ifPresent(cache -> cache.put(key, bytes));
  }
}
