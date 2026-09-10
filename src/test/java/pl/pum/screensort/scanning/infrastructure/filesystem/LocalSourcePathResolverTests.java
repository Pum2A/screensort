package pl.pum.screensort.scanning.infrastructure.filesystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.pum.screensort.scanning.domain.AllowedRoot;
import pl.pum.screensort.scanning.domain.SourcePathAccessException;
import pl.pum.screensort.scanning.domain.UnsafeSourcePathException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalSourcePathResolverTests {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void resolvesAnExistingDirectoryToItsCanonicalPath() throws IOException {
        Path source = Files.createDirectory(this.temporaryDirectory.resolve("source"));
        LocalSourcePathResolver resolver = resolver();

        Path resolved = resolver.resolveAllowedRoot(source.resolve("."));

        assertThat(resolved).isEqualTo(source.toRealPath());
    }

    @Test
    void rejectsAFileAsAnAllowedRoot() throws IOException {
        Path file = Files.writeString(this.temporaryDirectory.resolve("image.png"), "demo");

        assertThatThrownBy(() -> resolver().resolveAllowedRoot(file))
                .isInstanceOf(UnsafeSourcePathException.class)
                .hasMessageContaining("not a directory");
    }

    @Test
    void rejectsANonexistentAllowedRoot() {
        Path missing = this.temporaryDirectory.resolve("missing");

        assertThatThrownBy(() -> resolver().resolveAllowedRoot(missing))
                .isInstanceOf(SourcePathAccessException.class);
    }

    @Test
    void rejectsTheFilesystemRoot() {
        Path filesystemRoot = this.temporaryDirectory.getRoot();

        assertThatThrownBy(() -> resolver().resolveAllowedRoot(filesystemRoot))
                .isInstanceOf(UnsafeSourcePathException.class)
                .hasMessageContaining("filesystem roots");
    }

    @Test
    void rejectsTheEntireUserHome() {
        LocalSourcePathResolver resolver =
                new LocalSourcePathResolver(this.temporaryDirectory, List.of());

        assertThatThrownBy(() -> resolver.resolveAllowedRoot(this.temporaryDirectory))
                .isInstanceOf(UnsafeSourcePathException.class)
                .hasMessageContaining("entire user home");
    }

    @Test
    void rejectsProtectedLocationsAndTheirChildren() throws IOException {
        Path protectedRoot = Files.createDirectory(this.temporaryDirectory.resolve("system"));
        Path child = Files.createDirectory(protectedRoot.resolve("child"));
        LocalSourcePathResolver resolver =
                new LocalSourcePathResolver(this.temporaryDirectory.resolve("home"), List.of(protectedRoot));

        assertThatThrownBy(() -> resolver.resolveAllowedRoot(child))
                .isInstanceOf(UnsafeSourcePathException.class)
                .hasMessageContaining("system locations");
    }

    @Test
    void resolvesARegularFileInsideTheAllowedRoot() throws IOException {
        Path source = Files.createDirectory(this.temporaryDirectory.resolve("source"));
        Path image = Files.writeString(source.resolve("image.png"), "demo");
        LocalSourcePathResolver resolver = resolver();
        AllowedRoot allowedRoot = new AllowedRoot(source.toRealPath(), false);

        Path resolved = resolver.resolveExistingFile(allowedRoot, Path.of("image.png"));

        assertThat(resolved).isEqualTo(image.toRealPath());
    }

    @Test
    void rejectsTraversalOutsideTheAllowedRoot() throws IOException {
        Path source = Files.createDirectory(this.temporaryDirectory.resolve("source"));
        Path outside = Files.writeString(this.temporaryDirectory.resolve("outside.png"), "demo");
        LocalSourcePathResolver resolver = resolver();
        AllowedRoot allowedRoot = new AllowedRoot(source.toRealPath(), false);

        assertThatThrownBy(() -> resolver.resolveExistingFile(allowedRoot, source.resolve("..").resolve(outside.getFileName())))
                .isInstanceOf(UnsafeSourcePathException.class)
                .hasMessageContaining("outside the allowed root");
    }

    @Test
    void rejectsALinkThatEscapesTheAllowedRoot() throws Exception {
        Path source = Files.createDirectory(this.temporaryDirectory.resolve("source"));
        Path outsideDirectory = Files.createDirectory(this.temporaryDirectory.resolve("outside"));
        Path outsideFile = Files.writeString(outsideDirectory.resolve("outside.png"), "demo");
        Path link = createDirectoryLink(source.resolve("link"), outsideDirectory);
        LocalSourcePathResolver resolver = resolver();
        AllowedRoot allowedRoot = new AllowedRoot(source.toRealPath(), false);

        assertThatThrownBy(() -> resolver.resolveExistingFile(allowedRoot, link.resolve(outsideFile.getFileName())))
                .isInstanceOf(UnsafeSourcePathException.class)
                .hasMessageContaining("outside the allowed root");
    }

    private LocalSourcePathResolver resolver() {
        return new LocalSourcePathResolver(this.temporaryDirectory.resolve("home"), List.of());
    }

    private static Path createDirectoryLink(Path link, Path target) throws Exception {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            if (!System.getProperty("os.name").startsWith("Windows")) {
                throw exception;
            }
        }

        Process process = new ProcessBuilder(
                "cmd.exe", "/d", "/c", "mklink", "/J", link.toString(), target.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Could not create test junction: " + output);
        }
        return link;
    }

}
