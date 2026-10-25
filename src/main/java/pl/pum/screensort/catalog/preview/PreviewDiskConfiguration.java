package pl.pum.screensort.catalog.preview;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.pum.screensort.diagnostics.ProductMetrics;

/** Off unless a cache folder is configured, so tests and dev runs never write previews to disk. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("screensort.preview.cache-dir")
public class PreviewDiskConfiguration {
  @Bean
  DiskPreviewCache diskPreviewCache(
      @Value("${screensort.preview.cache-dir}") String directory,
      @Value("${screensort.preview.cache-max-mb:256}") long maxMegabytes,
      ProductMetrics metrics) {
    if (maxMegabytes < 1) throw new IllegalArgumentException("Preview cache limit must be positive");
    return new DiskPreviewCache(Path.of(directory), maxMegabytes * 1024 * 1024, metrics);
  }
}
