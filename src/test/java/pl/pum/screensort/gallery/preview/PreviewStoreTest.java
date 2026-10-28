package pl.pum.screensort.gallery.preview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.pum.screensort.metrics.ProductMetrics;

class PreviewStoreTest {
  @Test void worksWithNoRedisAndProtectsStoredBytes() {
    var store = new PreviewStore(Optional.empty(), Optional.empty(), new ProductMetrics(new SimpleMeterRegistry()));
    byte[] png = {1, 2, 3};
    store.put("preview", png);
    png[0] = 99;
    byte[] first = store.get("preview");
    first[1] = 99;
    assertThat(store.get("preview")).containsExactly((byte) 1, (byte) 2, (byte) 3);
  }

  @Test void remoteHitPopulatesMemorySoTheNextReadNeverCallsRedis() {
    var redis = mock(RedisPreviewCache.class);
    when(redis.get("preview")).thenReturn(new byte[] {1, 2});
    var store = new PreviewStore(Optional.empty(), Optional.of(redis), new ProductMetrics(new SimpleMeterRegistry()));
    assertThat(store.get("preview")).containsExactly((byte) 1, (byte) 2);
    assertThat(store.get("preview")).containsExactly((byte) 1, (byte) 2);
    verify(redis, times(1)).get("preview");
  }

  @Test void diskHitPopulatesMemoryAndSkipsRedis() {
    var disk = mock(DiskPreviewCache.class);
    var redis = mock(RedisPreviewCache.class);
    when(disk.get("preview")).thenReturn(new byte[] {4});
    var store = new PreviewStore(Optional.of(disk), Optional.of(redis), new ProductMetrics(new SimpleMeterRegistry()));
    assertThat(store.get("preview")).containsExactly((byte) 4);
    assertThat(store.get("preview")).containsExactly((byte) 4);
    verify(disk, times(1)).get("preview");
    verifyNoInteractions(redis);
  }

  @Test void redisHitIsKeptOnDiskForTheNextStart() {
    var disk = mock(DiskPreviewCache.class);
    var redis = mock(RedisPreviewCache.class);
    when(redis.get("preview")).thenReturn(new byte[] {5});
    var store = new PreviewStore(Optional.of(disk), Optional.of(redis), new ProductMetrics(new SimpleMeterRegistry()));
    assertThat(store.get("preview")).containsExactly((byte) 5);
    verify(disk).put(eq("preview"), aryEq(new byte[] {5}));
  }

  @Test void newPreviewIsWrittenToEveryLayer() {
    var disk = mock(DiskPreviewCache.class);
    var store = new PreviewStore(Optional.of(disk), Optional.empty(), new ProductMetrics(new SimpleMeterRegistry()));
    store.put("preview", new byte[] {6});
    verify(disk).put(eq("preview"), aryEq(new byte[] {6}));
    assertThat(store.get("preview")).containsExactly((byte) 6);
  }
}
