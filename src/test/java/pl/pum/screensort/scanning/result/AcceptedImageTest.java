package pl.pum.screensort.scanning.result;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AcceptedImageTest {

  @Test
  void formatsFileSizeUsingTheAppropriateUnit() {
    assertEquals("512 B", imageWithSize(512).formattedSize());
    assertEquals(String.format("%.2f KB", 1.0), imageWithSize(1024).formattedSize());
    assertEquals(String.format("%.2f MB", 1.0), imageWithSize(1024L * 1024).formattedSize());
    assertEquals(String.format("%.2f GB", 1.0), imageWithSize(1024L * 1024 * 1024).formattedSize());
  }

  private AcceptedImage imageWithSize(long sizeBytes) {
    return new AcceptedImage(
        Path.of("image.png"), "image.png", "png", sizeBytes, Instant.EPOCH, "123123123123");
  }
}
