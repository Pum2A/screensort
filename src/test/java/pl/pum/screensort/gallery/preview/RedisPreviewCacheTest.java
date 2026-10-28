package pl.pum.screensort.gallery.preview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import pl.pum.screensort.diagnostics.ProductMetrics;

class RedisPreviewCacheTest {
  @SuppressWarnings("unchecked")
  private final RedisTemplate<String, byte[]> template = mock(RedisTemplate.class);
  @SuppressWarnings("unchecked")
  private final ValueOperations<String, byte[]> values = mock(ValueOperations.class);
  private final AtomicLong clock = new AtomicLong();
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  private RedisPreviewCache cache() {
    when(template.opsForValue()).thenReturn(values);
    return new RedisPreviewCache(template, new ProductMetrics(registry), clock::get);
  }

  @Test void writesWithBoundedTtlAndIgnoresOversizedPreviews() {
    var cache = cache();
    byte[] png = {1, 2};
    cache.put("key", png);
    cache.put("huge", new byte[RedisPreviewCache.MAX_BYTES + 1]);
    verify(values).set("key", png, RedisPreviewCache.TTL);
    verify(values, never()).set(eq("huge"), any(byte[].class), any(java.time.Duration.class));
  }

  @Test void outageFallsBackAndBacksOffBeforeRetrying() {
    var cache = cache();
    when(values.get("key")).thenThrow(new RedisConnectionFailureException("offline"))
        .thenReturn(new byte[] {5});
    assertThat(cache.get("key")).isNull();
    assertThat(cache.get("key")).isNull();
    cache.put("key", new byte[] {1});
    verify(values, times(1)).get("key");
    verify(values, never()).set(anyString(), any(byte[].class), any(java.time.Duration.class));
    clock.addAndGet(RedisPreviewCache.BACKOFF_NANOS + 1);
    assertThat(cache.get("key")).containsExactly((byte) 5);
    assertThat(registry.get("screensort.preview.cache").tag("outcome", "error").counter().count()).isOne();
  }

  @Test void writeFailureNeverPreventsTheCallerFromUsingItsPreview() {
    var cache = cache();
    doThrow(new RedisConnectionFailureException("offline"))
        .when(values).set(anyString(), any(byte[].class), any(java.time.Duration.class));
    assertThatCode(() -> cache.put("key", new byte[] {1})).doesNotThrowAnyException();
    assertThat(cache.get("key")).isNull();
    verify(values, never()).get(anyString());
  }
}
