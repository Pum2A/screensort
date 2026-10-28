package pl.pum.screensort.gallery.preview;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static pl.pum.screensort.gallery.preview.ImagePreviewException.Reason.BUSY;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

/**
 * Bounds memory by decoding only a few images at once. Later requests wait their turn
 * (first come, first served) instead of failing, and identical requests share one decode.
 */
final class PreviewDecodeQueue {
  @FunctionalInterface
  interface Decoder {
    byte[] decode() throws IOException;
  }

  private final Semaphore decoders;
  private final long maxWaitNanos;
  private final ConcurrentHashMap<String, CompletableFuture<byte[]>> inFlight = new ConcurrentHashMap<>();

  PreviewDecodeQueue(int decoders, Duration maxWait) {
    // Fair: a request that arrived earlier gets the next free decoder.
    this.decoders = new Semaphore(decoders, true);
    this.maxWaitNanos = maxWait.toNanos();
  }

  byte[] decode(String key, Decoder decoder) throws IOException, InterruptedException {
    var own = new CompletableFuture<byte[]>();
    var running = inFlight.putIfAbsent(key, own);
    if (running != null) return awaitShared(running);
    try {
      if (!decoders.tryAcquire(maxWaitNanos, NANOSECONDS)) throw busy();
      try {
        byte[] result = decoder.decode();
        own.complete(result);
        return result;
      } finally {
        decoders.release();
      }
    } catch (Throwable failure) {
      own.completeExceptionally(failure);
      throw failure;
    } finally {
      inFlight.remove(key, own);
    }
  }

  private byte[] awaitShared(CompletableFuture<byte[]> running) throws IOException, InterruptedException {
    try {
      return running.get(maxWaitNanos, NANOSECONDS);
    } catch (TimeoutException e) {
      throw busy();
    } catch (ExecutionException e) {
      // The waiting request reports the same reason as the one that decoded.
      if (e.getCause() instanceof IOException io) throw io;
      if (e.getCause() instanceof RuntimeException runtime) throw runtime;
      if (e.getCause() instanceof InterruptedException) throw busy();
      throw new IllegalStateException("Preview decode failed", e.getCause());
    }
  }

  private static ImagePreviewException busy() {
    return new ImagePreviewException(BUSY, "Podglądy są zajęte. Spróbuj ponownie za chwilę.");
  }
}
