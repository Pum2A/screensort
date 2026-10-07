package pl.pum.screensort.scanning.workers;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ScanJobRunnerConfiguration {
  @Bean
  public ThreadPoolTaskExecutor scanJobRunner() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    // Jedno miejsce na przekazanie następnego skanu, gdy poprzedni wątek kończy powrót.
    // Manager nie pozwala kolejkować kolejnych aktywnych skanów.
    executor.setQueueCapacity(1);
    executor.setThreadNamePrefix("scan-job-");
    executor.setStrictEarlyShutdown(true);
    executor.setWaitForTasksToCompleteOnShutdown(false);
    executor.setAwaitTerminationSeconds(5);
    return executor;
  }
}
