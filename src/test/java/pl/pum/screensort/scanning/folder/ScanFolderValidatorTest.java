package pl.pum.screensort.scanning.folder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScanFolderValidatorTest {

    private final ScanFolderValidator validator = new ScanFolderValidator();

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsAReadableDirectory() throws Exception {
        AllowedRoot allowedRoot = validator.validate(temporaryDirectory);

        assertEquals(
                temporaryDirectory.toRealPath(),
                allowedRoot.path()
        );
    }

    @Test
    void rejectsANonexistentDirectory() {
        Path nonexistentDirectory = temporaryDirectory.resolve("missing");

        assertThrows(
                InvalidScanFolderException.class,
                () -> validator.validate(nonexistentDirectory)
        );
    }

    @Test
    void rejectsAFileInsteadOfADirectory() throws Exception {
        Path file = Files.createFile(temporaryDirectory.resolve("image.png"));

        assertThrows(
                InvalidScanFolderException.class,
                () -> validator.validate(file)
        );
    }

    @Test
    void rejectsTheFilesystemRoot() {
        Path filesystemRoot = temporaryDirectory.toAbsolutePath().getRoot();

        assertThrows(
                InvalidScanFolderException.class,
                () -> validator.validate(filesystemRoot)
        );
    }

    @Test
    void rejectsTheCurrentUserHomeDirectory() {
        Path userHome = Path.of(System.getProperty("user.home"));

        assertThrows(
                InvalidScanFolderException.class,
                () -> validator.validate(userHome)
        );
    }
}
