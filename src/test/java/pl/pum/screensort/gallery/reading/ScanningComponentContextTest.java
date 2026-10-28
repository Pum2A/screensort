package pl.pum.screensort.gallery.reading;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class ScanningComponentContextTest {

  @Test
  void createsScannerWithAllRequiredDependencies() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(
          FileHasher.class,
          ImageScanner.class,
          ScanWorkersConfiguration.class,
          ScanJobRunnerConfiguration.class);
      context.refresh();

      assertNotNull(context.getBean(FileHasher.class));
      assertNotNull(context.getBean(ImageScanner.class));
      ThreadPoolTaskExecutor runner =
          context.getBean("scanJobRunner", ThreadPoolTaskExecutor.class);
      assertNotSame(context.getBean("scanWorkers", ExecutorService.class), runner);
      assertEquals(1, runner.getCorePoolSize());
      assertEquals(1, runner.getMaxPoolSize());
      assertEquals(1, runner.getQueueCapacity());
    }
  }

  @Test
  void springInitializesAndClosesTheDedicatedRunner() throws Exception {
    ThreadPoolTaskExecutor runner;
    try (var context = new AnnotationConfigApplicationContext(ScanJobRunnerConfiguration.class)) {
      runner = context.getBean("scanJobRunner", ThreadPoolTaskExecutor.class);
      String threadName =
          runner.submit(() -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS);
      assertTrue(threadName.startsWith("scan-job-"));
    }
    assertTrue(runner.getThreadPoolExecutor().isShutdown());
  }
}
