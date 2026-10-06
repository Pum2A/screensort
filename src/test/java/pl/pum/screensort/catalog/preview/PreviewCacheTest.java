package pl.pum.screensort.catalog.preview;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class PreviewCacheTest {
  @Test
  void evictsLeastRecentlyUsedEntriesAndProtectsCachedBytes() {
    var cache = new PreviewCache(2, 20);
    cache.put("a", new byte[] {1});
    cache.put("b", new byte[] {2});
    byte[] read = cache.get("a");
    read[0] = 99;
    cache.put("c", new byte[] {3});
    assertThat(cache.get("b")).isNull();
    assertThat(cache.get("a")).containsExactly((byte) 1);
    assertThat(cache.get("c")).containsExactly((byte) 3);
  }

  @Test
  void respectsByteBudgetIncludingReplacementsAndOversizedEntries() {
    var cache = new PreviewCache(10, 5);
    cache.put("a", new byte[3]);
    cache.put("b", new byte[3]);
    assertThat(cache.get("a")).isNull();
    cache.put("b", new byte[1]);
    cache.put("c", new byte[4]);
    assertThat(cache.get("b")).hasSize(1);
    assertThat(cache.get("c")).hasSize(4);
    cache.put("big", new byte[6]);
    assertThat(cache.get("big")).isNull();
    assertThat(cache.get("b")).hasSize(1);
  }
}
