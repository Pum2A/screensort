package pl.pum.screensort.scanning;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageScannerTest {

    @Test
    void findsSupportedImagesInDemoFolder() throws Exception {
        ImageScanner scanner = new ImageScanner();

        List<ScannedImage> images = scanner.scan(Path.of("demo-images"));

        assertEquals(2, images.size());
    }
}
