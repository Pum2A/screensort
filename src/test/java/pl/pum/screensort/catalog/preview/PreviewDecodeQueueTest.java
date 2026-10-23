package pl.pum.screensort.catalog.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PreviewDecodeQueueTest {
  private final ExecutorService requests = Executors.newFixedThreadPool(4);

  @AfterEach
  void tearDown() {
    requests.shutdownNow();
  }

  @Test
  void requestBeyondTheDecoderLimitWaitsInsteadOfFailing() throws Exception {
    var queue = new PreviewDecodeQueue(2, Duration.ofSeconds(5));
    var started = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    Future<byte[]> first = requests.submit(() -> queue.decode("a", () -> blockUntil(started, release, 1)));
    Future<byte[]> second = requests.submit(() -> queue.decode("b", () -> blockUntil(started, release, 2)));
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

    Future<byte[]> third = requests.submit(() -> queue.decode("c", () -> new byte[] {3}));
    // Both decoders stay busy past the old 2 s limit, after which the third request got BUSY.
    Thread.sleep(2_500);
    assertThat(third.isDone()).isFalse();

    release.countDown();
    assertThat(first.get(5, TimeUnit.SECONDS)).containsExactly(1);
    assertThat(second.get(5, TimeUnit.SECONDS)).containsExactly(2);
    assertThat(third.get(5, TimeUnit.SECONDS)).containsExactly(3);
  }

  @Test
  void identicalRequestsShareOneDecode() throws Exception {
    var queue = new PreviewDecodeQueue(2, Duration.ofSeconds(5));
    var decodes = new AtomicInteger();
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    Future<byte[]> leader = requests.submit(() -> queue.decode("same", () -> {
      decodes.incrementAndGet();
      return blockUntil(started, release, 7);
    }));
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    Future<byte[]> follower = requests.submit(() -> queue.decode("same", () -> {
      decodes.incrementAndGet();
      return new byte[] {9};
    }));
    // Give the second request time to join the running decode before it finishes.
    Thread.sleep(300);

    release.countDown();
    assertThat(leader.get(5, TimeUnit.SECONDS)).containsExactly(7);
    assertThat(follower.get(5, TimeUnit.SECONDS)).containsExactly(7);
    assertThat(decodes).hasValue(1);
  }

  @Test
  void sharedFailureReachesTheWaitingRequestAndDoesNotBlockTheNextOne() throws Exception {
    var queue = new PreviewDecodeQueue(2, Duration.ofSeconds(5));
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    Future<byte[]> leader = requests.submit(() -> queue.decode("file", () -> {
      blockUntil(started, release, 0);
      throw new ImagePreviewException(ImagePreviewException.Reason.CHANGED, "changed");
    }));
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    Future<byte[]> follower = requests.submit(() -> queue.decode("file", () -> new byte[] {1}));
    Thread.sleep(300);

    release.countDown();
    assertThatThrownBy(() -> leader.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(ImagePreviewException.class);
    assertThatThrownBy(() -> follower.get(5, TimeUnit.SECONDS))
        .cause().isInstanceOfSatisfying(ImagePreviewException.class,
            e -> assertThat(e.reason()).isEqualTo(ImagePreviewException.Reason.CHANGED));
    assertThat(queue.decode("file", () -> new byte[] {2})).containsExactly(2);
  }

  @Test
  void waitingLongerThanTheLimitIsReportedAsBusy() throws Exception {
    var queue = new PreviewDecodeQueue(1, Duration.ofMillis(100));
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    Future<byte[]> holder = requests.submit(() -> queue.decode("a", () -> blockUntil(started, release, 1)));
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

    assertThatThrownBy(() -> queue.decode("b", () -> new byte[] {2}))
        .isInstanceOfSatisfying(ImagePreviewException.class,
            e -> assertThat(e.reason()).isEqualTo(ImagePreviewException.Reason.BUSY));
    release.countDown();
    assertThat(holder.get(5, TimeUnit.SECONDS)).containsExactly(1);
  }

  private static byte[] blockUntil(CountDownLatch started, CountDownLatch release, int value) {
    started.countDown();
    try {
      if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test was not released");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
    return new byte[] {(byte) value};
  }
}
