package pl.pum.screensort.scanning.image;

import org.junit.jupiter.api.Test;
import pl.pum.screensort.scanning.safety.ScanFolderValidator;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageScannerTest {

    @Test
    void findsSupportedImagesInDemoFolder() throws Exception {
        ImageScanner scanner = new ImageScanner(new ScanFolderValidator());

        ScanResult result = scanner.scan(Path.of("demo-images"));

        assertEquals(2, result.accepted().size());   // dwa PNG przeszły
        assertEquals(1, result.rejected().size());   // ZIP trafił do odrzuconych
    }
}
