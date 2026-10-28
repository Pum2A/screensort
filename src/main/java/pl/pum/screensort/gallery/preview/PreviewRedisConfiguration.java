package pl.pum.screensort.gallery.preview;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import pl.pum.screensort.diagnostics.ProductMetrics;

/** Developer-only opt-in. Never starts a Redis client in the desktop installer. */
@Configuration(proxyBeanMethods = false)
@Profile("redis-cache & !desktop")
public class PreviewRedisConfiguration {
  @Bean
  LettuceConnectionFactory previewRedisConnectionFactory(
      @Value("${screensort.preview.redis.port:16379}") int port) {
    if (port <= 0 || port > 65535) throw new IllegalArgumentException("Invalid local Redis port");
    var timeout = Duration.ofMillis(100);
    var options = ClientOptions.builder().autoReconnect(false)
        .socketOptions(SocketOptions.builder().connectTimeout(timeout).build()).build();
    var client = LettuceClientConfiguration.builder().commandTimeout(timeout)
        .shutdownTimeout(Duration.ZERO).clientOptions(options).build();
    var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", port), client);
    factory.setValidateConnection(true);
    return factory;
  }

  @Bean
  RedisTemplate<String, byte[]> previewRedisTemplate(LettuceConnectionFactory previewRedisConnectionFactory) {
    var template = new RedisTemplate<String, byte[]>();
    template.setConnectionFactory(previewRedisConnectionFactory);
    template.setEnableDefaultSerializer(false);
    template.setKeySerializer(RedisSerializer.string());
    template.setValueSerializer(RedisSerializer.byteArray());
    return template;
  }

  @Bean
  RedisPreviewCache redisPreviewCache(RedisTemplate<String, byte[]> previewRedisTemplate, ProductMetrics metrics) {
    return new RedisPreviewCache(previewRedisTemplate, metrics);
  }
}
