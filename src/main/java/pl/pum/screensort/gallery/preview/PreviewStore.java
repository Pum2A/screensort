package pl.pum.screensort.gallery.preview;

import java.util.Optional;
import org.springframework.stereotype.Component;
import pl.pum.screensort.metrics.ProductMetrics;
import static pl.pum.screensort.metrics.ProductMetrics.CacheLayer.MEMORY;
import static pl.pum.screensort.metrics.ProductMetrics.CacheOutcome.*;

/**
 * Small in-process LRU first, then the disk cache that survives restarts, then optional
 * short-lived Redis. None is authoritative. A hit in a slower layer is copied to the faster ones.
 */
@Component
public class PreviewStore {
  private final PreviewCache memory = new PreviewCache(128, 16L * 1024 * 1024);
  private final Optional<DiskPreviewCache> disk;
  private final Optional<RedisPreviewCache> redis;
  private final ProductMetrics metrics;

  public PreviewStore(Optional<DiskPreviewCache> disk, Optional<RedisPreviewCache> redis,
      ProductMetrics metrics) {
    this.disk = disk;
    this.redis = redis;
    this.metrics = metrics;
  }

  public byte[] get(String key) {
    byte[] bytes = memory.get(key);
    metrics.cache(MEMORY, bytes == null ? MISS : HIT);
    if (bytes != null) return bytes;
    bytes = disk.map(cache -> cache.get(key)).orElse(null);
    if (bytes != null) {
      memory.put(key, bytes);
      return bytes;
    }
    byte[] remote = redis.map(cache -> cache.get(key)).orElse(null);
    if (remote != null) {
      memory.put(key, remote);
      disk.ifPresent(cache -> cache.put(key, remote));
    }
    return remote;
  }

  public void put(String key, byte[] bytes) {
    memory.put(key, bytes);
    disk.ifPresent(cache -> cache.put(key, bytes));
    redis.ifPresent(cache -> cache.put(key, bytes));
  }
}
