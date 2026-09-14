package pl.pum.screensort.scanning.service;

import org.junit.jupiter.api.Test;
import pl.pum.screensort.scanning.model.ScannedImage;
import pl.pum.screensort.scanning.validation.ScanFolderValidator;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageScannerTest {

    @Test
    void findsSupportedImagesInDemoFolder() throws Exception {
        ImageScanner scanner = new ImageScanner(new ScanFolderValidator());

        List<ScannedImage> images = scanner.scan(Path.of("demo-images"));

        assertEquals(2, images.size());
    }
}
