package pl.pum.screensort.metrics;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ObservabilityProfileTest {
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withInitializer(new ConfigDataApplicationContextInitializer());

  @Test void onlyTheOptInDeveloperProfileExposesPrometheusOnLoopback() {
    runner.withPropertyValues("spring.profiles.active=observability").run(context -> {
      var env = context.getEnvironment();
      assertThat(env.getProperty("management.server.address")).isEqualTo("127.0.0.1");
      assertThat(env.getProperty("management.server.port")).isEqualTo("19091");
      assertThat(env.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,prometheus");
    });
  }

  @Test void desktopNeverInheritsTheDeveloperManagementPort() {
    runner.withPropertyValues("spring.profiles.active=desktop,observability").run(context -> {
      var env = context.getEnvironment();
      assertThat(env.getProperty("management.server.port")).isNull();
      assertThat(env.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
    });
    runner.run(context ->
        assertThat(context.getEnvironment().getProperty("management.endpoints.web.exposure.include"))
            .doesNotContain("prometheus"));
  }
}
