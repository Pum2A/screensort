package pl.pum.screensort.catalog.preview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.pum.screensort.catalog.preview.redis.RedisPreviewCache;
import pl.pum.screensort.diagnostics.ProductMetrics;

class PreviewStoreTest {
  @Test void worksWithNoRedisAndProtectsStoredBytes() {
    var store = new PreviewStore(Optional.empty(), new ProductMetrics(new SimpleMeterRegistry()));
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
    var store = new PreviewStore(Optional.of(redis), new ProductMetrics(new SimpleMeterRegistry()));
    assertThat(store.get("preview")).containsExactly((byte) 1, (byte) 2);
    assertThat(store.get("preview")).containsExactly((byte) 1, (byte) 2);
    verify(redis, times(1)).get("preview");
  }
}
