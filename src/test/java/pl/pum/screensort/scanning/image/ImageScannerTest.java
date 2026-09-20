package pl.pum.screensort.scanning.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import pl.pum.screensort.scanning.hashing.FileHasher;
import pl.pum.screensort.scanning.safety.ScanFolderValidator;

class ImageScannerTest {

    @Test
    void findsSupportedImagesInDemoFolder() throws Exception {
    ImageScanner scanner = new ImageScanner(new ScanFolderValidator(), new FileHasher());

        ScanResult result = scanner.scan(Path.of("demo-images"));

        assertEquals(2, result.accepted().size());   // dwa PNG przeszły
        assertEquals(1, result.rejected().size());   // ZIP trafił do odrzuconych
    }
}
