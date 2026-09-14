package pl.pum.screensort.scanning.image;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScannedImageTest {

    @Test
    void formatsFileSizeUsingTheAppropriateUnit() {
        assertEquals("512 B", imageWithSize(512).formattedSize());
        assertEquals(String.format("%.2f KB", 1.0), imageWithSize(1024).formattedSize());
        assertEquals(String.format("%.2f MB", 1.0), imageWithSize(1024L * 1024).formattedSize());
        assertEquals(String.format("%.2f GB", 1.0), imageWithSize(1024L * 1024 * 1024).formattedSize());
    }

    private ScannedImage imageWithSize(long sizeBytes) {
        return new ScannedImage(
                Path.of("image.png"),
                "image.png",
                "png",
                sizeBytes,
                Instant.EPOCH
        );
    }
}
