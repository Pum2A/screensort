package pl.pum.screensort.scanning.infrastructure.filesystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.pum.screensort.scanning.domain.AllowedRoot;
import pl.pum.screensort.scanning.domain.DiscoveredImage;
import pl.pum.screensort.scanning.domain.ImageFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalImageDiscoveryTests {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void discoversOnlySupportedImagesDirectlyInsideANonRecursiveRoot() throws IOException {
        Path source = Files.createDirectory(this.temporaryDirectory.resolve("source"));
        Path directImage = Files.writeString(source.resolve("screen.PNG"), "image-data");
        Files.writeString(source.resolve("notes.txt"), "not an image");
        Path nested = Files.createDirectory(source.resolve("nested"));
        Files.writeString(nested.resolve("nested.jpg"), "nested-image");

        List<DiscoveredImage> images = discovery().discover(allowedRoot(source, false));

        assertThat(images).singleElement().satisfies(image -> {
            assertThat(image.canonicalPath()).isEqualTo(directImage.toRealPath());
            assertThat(image.relativePath()).isEqualTo(Path.of("screen.PNG"));
            assertThat(image.format()).isEqualTo(ImageFormat.PNG);
            assertThat(image.originalName()).isEqualTo("screen.PNG");
            assertThat(image.mediaType()).isEqualTo("image/png");
            assertThat(image.sizeBytes()).isEqualTo(Files.size(directImage));
            assertThat(image.lastModifiedAt()).isNotNull();
        });
    }

    @Test
    void recursivelyDiscoversPngJpgAndJpegFilesInStableOrder() throws IOException {
        Path source = Files.createDirectory(this.temporaryDirectory.resolve("source"));
        Path nested = Files.createDirectories(source.resolve("album").resolve("nested"));
        Files.writeString(source.resolve("z.jpeg"), "z");
        Files.writeString(source.resolve("a.jpg"), "a");
        Files.writeString(nested.resolve("middle.png"), "m");

        List<DiscoveredImage> images = discovery().discover(allowedRoot(source, true));

        assertThat(images)
                .extracting(image -> image.relativePath().toString().replace('\\', '/'))
                .containsExactly("a.jpg", "album/nested/middle.png", "z.jpeg");
        assertThat(images)
                .extracting(DiscoveredImage::format)
                .containsExactly(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.JPEG);
    }

    @Test
    void doesNotFollowADirectoryLinkOutsideTheAllowedRoot() throws Exception {
        Path source = Files.createDirectory(this.temporaryDirectory.resolve("source"));
        Files.writeString(source.resolve("inside.png"), "inside");
        Path outside = Files.createDirectory(this.temporaryDirectory.resolve("outside"));
        Files.writeString(outside.resolve("outside.png"), "outside");
        createDirectoryLink(source.resolve("linked"), outside);

        List<DiscoveredImage> images = discovery().discover(allowedRoot(source, true));

        assertThat(images)
                .extracting(image -> image.relativePath().toString())
                .containsExactly("inside.png");
    }

    private LocalImageDiscovery discovery() {
        LocalSourcePathResolver resolver =
                new LocalSourcePathResolver(this.temporaryDirectory.resolve("home"), List.of());
        return new LocalImageDiscovery(resolver);
    }

    private static AllowedRoot allowedRoot(Path source, boolean recursive) throws IOException {
        return new AllowedRoot(source.toRealPath(), recursive);
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
