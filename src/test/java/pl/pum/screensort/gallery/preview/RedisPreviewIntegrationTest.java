package pl.pum.screensort.gallery.preview;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.pum.screensort.metrics.ProductMetrics;

/** Synthetic bytes and an isolated, ephemeral Redis. No access to the developer cache or images. */
@Testcontainers(disabledWithoutDocker = true)
class RedisPreviewIntegrationTest {
  @Container
  static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.6.6-alpine"))
      .withExposedPorts(6379)
      .withCommand("redis-server", "--save", "", "--appendonly", "no", "--maxmemory", "64mb");

  @Test void roundTripsBytesWithAnExpiryAndNoJavaObjectDeserialization() {
    var client = LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).build();
    var factory = new LettuceConnectionFactory(
        new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)), client);
    factory.afterPropertiesSet();
    try {
      var template = new RedisTemplate<String, byte[]>();
      template.setConnectionFactory(factory);
      template.setEnableDefaultSerializer(false);
      template.setKeySerializer(RedisSerializer.string());
      template.setValueSerializer(RedisSerializer.byteArray());
      template.afterPropertiesSet();
      var cache = new RedisPreviewCache(template, new ProductMetrics(new SimpleMeterRegistry()));
      String key = "screensort:preview:v1:" + "a".repeat(64);
      byte[] png = {1, 2, 3, 4};
      cache.put(key, png);
      assertThat(cache.get(key)).isEqualTo(png);
      assertThat(template.getExpire(key)).isBetween(1L, RedisPreviewCache.TTL.toSeconds());
    } finally {
      factory.destroy();
    }
  }
}
