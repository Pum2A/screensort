package pl.pum.screensort.diagnostics;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pl.pum.screensort.gallery.reading.ScanSummary;

/** Real HTTP export on random local ports; no catalog, database or model processes. */
class PrometheusEndpointIntegrationTest {
  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration(excludeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
  @Import(ProductMetrics.class)
  static class MetricsApp {}

  @Test @Timeout(30)
  void exportsMetricsButNotEnvironmentOrHeapDump() throws Exception {
    try (var context = new SpringApplicationBuilder(MetricsApp.class).profiles("observability")
        .run("--server.port=0", "--management.server.port=0", "--spring.docker.compose.enabled=false")) {
      context.getBean(ProductMetrics.class).scanned(new ScanSummary(1, 0, 2, 0));
      String base = "http://127.0.0.1:" + context.getEnvironment().getRequiredProperty("local.management.port");
      try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
        var request = HttpRequest.newBuilder(URI.create(base + "/actuator/prometheus"))
            .timeout(Duration.ofSeconds(3)).GET().build();
        var response = http.send(request, BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("screensort_scan_files_total{outcome=\"unchanged\"} 2.0");
        for (String hidden : new String[] {"env", "heapdump", "configprops", "logfile"}) {
          var hiddenRequest = HttpRequest.newBuilder(URI.create(base + "/actuator/" + hidden))
              .timeout(Duration.ofSeconds(3)).GET().build();
          assertThat(http.send(hiddenRequest, BodyHandlers.discarding()).statusCode()).isEqualTo(404);
        }
      }
    }
  }
}
