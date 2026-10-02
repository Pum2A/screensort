package pl.pum.screensort.scanning.workers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScanWorkersConfiguration {
  @Bean(destroyMethod = "close")
  public ExecutorService scanWorkers() {
    return Executors.newFixedThreadPool(2);
  }
}
