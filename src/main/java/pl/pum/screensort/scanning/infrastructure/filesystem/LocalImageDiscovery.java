package pl.pum.screensort.scanning.infrastructure.filesystem;

import org.springframework.stereotype.Component;
import pl.pum.screensort.scanning.application.port.ImageDiscovery;
import pl.pum.screensort.scanning.application.port.SourcePathResolver;
import pl.pum.screensort.scanning.domain.AllowedRoot;
import pl.pum.screensort.scanning.domain.DiscoveredImage;
import pl.pum.screensort.scanning.domain.ImageFormat;
import pl.pum.screensort.scanning.domain.SourcePathAccessException;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Read-only local-filesystem implementation of the image-discovery port.
 */
@Component
public class LocalImageDiscovery implements ImageDiscovery {

    private static final int DIRECT_CHILDREN_DEPTH = 1;

    private final SourcePathResolver sourcePathResolver;

    public LocalImageDiscovery(SourcePathResolver sourcePathResolver) {
        this.sourcePathResolver = sourcePathResolver;
    }

    @Override
    public List<DiscoveredImage> discover(AllowedRoot allowedRoot) {
        int maximumDepth = allowedRoot.recursiveScan() ? Integer.MAX_VALUE : DIRECT_CHILDREN_DEPTH;
        List<DiscoveredImage> discoveredImages = new ArrayList<>();

        try {
            Files.walkFileTree(
                    allowedRoot.canonicalPath(),
                    EnumSet.noneOf(java.nio.file.FileVisitOption.class),
                    maximumDepth,
                    visitorFor(allowedRoot, discoveredImages));
        } catch (IOException exception) {
            throw new SourcePathAccessException(allowedRoot.canonicalPath(), exception);
        }

        return discoveredImages.stream()
                .sorted(Comparator.comparing(image -> portablePath(image.relativePath())))
                .toList();
    }

    private FileVisitor<Path> visitorFor(AllowedRoot allowedRoot, List<DiscoveredImage> discoveredImages) {
        return new FileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (!directory.equals(allowedRoot.canonicalPath()) && isDirectoryLink(directory)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && !Files.isSymbolicLink(file)) {
                    toDiscoveredImage(allowedRoot, file).ifPresent(discoveredImages::add);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exception) throws IOException {
                throw exception;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                return FileVisitResult.CONTINUE;
            }
        };
    }

    private static boolean isDirectoryLink(Path directory) throws IOException {
        // A junction or symbolic link resolves to a different physical path.
        Path lexicalPath = directory.toAbsolutePath().normalize();
        Path canonicalPath = directory.toRealPath();
        return !canonicalPath.equals(lexicalPath);
    }

    private Optional<DiscoveredImage> toDiscoveredImage(AllowedRoot allowedRoot, Path discoveredPath) {
        Optional<ImageFormat> format = ImageFormat.fromPath(discoveredPath);
        if (format.isEmpty()) {
            return Optional.empty();
        }

        Path canonicalPath = this.sourcePathResolver.resolveExistingFile(allowedRoot, discoveredPath);
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    canonicalPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            Path relativePath = allowedRoot.canonicalPath().relativize(canonicalPath);
            return Optional.of(new DiscoveredImage(
                    canonicalPath,
                    relativePath,
                    format.orElseThrow(),
                    attributes.size(),
                    attributes.lastModifiedTime().toInstant()));
        } catch (IOException exception) {
            throw new SourcePathAccessException(canonicalPath, exception);
        }
    }

    private static String portablePath(Path path) {
        return path.toString().replace('\\', '/');
    }

}
