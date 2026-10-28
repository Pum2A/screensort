package pl.pum.screensort.metrics;

import static org.assertj.core.api.Assertions.*;
import static pl.pum.screensort.metrics.ProductMetrics.Operation.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import pl.pum.screensort.gallery.reading.ScanSummary;

class ProductMetricsTest {
  @Test void recordsSuccessAndCheckedFailureWithoutSwallowingExceptions() throws Exception {
    var registry = new SimpleMeterRegistry();
    var metrics = new ProductMetrics(registry);
    assertThat(metrics.measure(SCAN, () -> "result")).isEqualTo("result");
    var failure = new IOException("private/source.png");
    assertThatThrownBy(() -> metrics.measure(SCAN, () -> { throw failure; })).isSameAs(failure);
    assertThat(registry.get("screensort.operation").tag("outcome", "success").timer().count()).isOne();
    assertThat(registry.get("screensort.operation").tag("outcome", "failure").timer().count()).isOne();
    assertThat(registry.getMeters()).allSatisfy(meter ->
        assertThat(meter.getId().getTags()).noneSatisfy(tag -> assertThat(tag.getValue()).contains("private")));
  }

  @Test void exposesRealPrometheusCountersWithoutSourceIdentifiers() {
    var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    try {
      var metrics = new ProductMetrics(registry);
      metrics.scanned(new ScanSummary(3, 1, 10, 2));
      String scrape = registry.scrape();
      assertThat(scrape).contains("screensort_scan_files_total{outcome=\"unchanged\"} 10.0");
      assertThat(scrape).doesNotContain("sourceFolder", "sha256", "fileId", "originalName");
    } finally {
      registry.close();
    }
  }
}
