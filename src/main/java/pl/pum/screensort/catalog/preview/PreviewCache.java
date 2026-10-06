package pl.pum.screensort.catalog.preview;

import java.util.LinkedHashMap;
import java.util.Map;

// Tylko zakodowane miniatury, nigdy pełne obrazy. Najdawniej używane wpisy usuwamy pierwsze.
final class PreviewCache {
  private final int maxEntries;
  private final long maxBytes;
  private final Map<Object, byte[]> entries = new LinkedHashMap<>(16, 0.75f, true);
  private long usedBytes;

  PreviewCache(int maxEntries, long maxBytes) {
    this.maxEntries = maxEntries;
    this.maxBytes = maxBytes;
  }

  synchronized byte[] get(Object key) {
    byte[] bytes = entries.get(key);
    return bytes == null ? null : bytes.clone();
  }

  synchronized void put(Object key, byte[] bytes) {
    if (bytes.length > maxBytes) return;
    byte[] previous = entries.put(key, bytes.clone());
    usedBytes += bytes.length - (previous == null ? 0 : previous.length);

    var iterator = entries.entrySet().iterator();
    while (entries.size() > maxEntries || usedBytes > maxBytes) {
      usedBytes -= iterator.next().getValue().length;
      iterator.remove();
    }
  }
}
