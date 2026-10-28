package pl.pum.screensort.gallery.preview;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import pl.pum.screensort.diagnostics.ProductMetrics;

class PreviewRedisConfigurationTest {
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(PreviewRedisConfiguration.class)
      .withBean(ProductMetrics.class, () -> new ProductMetrics(new SimpleMeterRegistry()));

  @Test void noRedisClientIsCreatedByDefaultOrInDesktopEvenIfTheProfileIsRequested() {
    runner.run(context -> assertThat(context).doesNotHaveBean(LettuceConnectionFactory.class));
    runner.withPropertyValues("spring.profiles.active=desktop,redis-cache")
        .run(context -> assertThat(context).doesNotHaveBean(LettuceConnectionFactory.class));
  }

  @Test void explicitDeveloperProfileCreatesAnIdleLoopbackClient() {
    runner.withPropertyValues("spring.profiles.active=redis-cache")
        .run(context -> {
          assertThat(context).hasSingleBean(RedisPreviewCache.class);
          var factory = context.getBean(LettuceConnectionFactory.class);
          assertThat(factory.getHostName()).isEqualTo("127.0.0.1");
          assertThat(factory.getPort()).isEqualTo(16379);
          assertThat(factory.getClientConfiguration().getCommandTimeout()).hasMillis(100);
        });
  }
}
