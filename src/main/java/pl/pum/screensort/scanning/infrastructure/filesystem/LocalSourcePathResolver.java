package pl.pum.screensort.scanning.infrastructure.filesystem;

import org.springframework.stereotype.Component;
import pl.pum.screensort.scanning.application.port.SourcePathResolver;
import pl.pum.screensort.scanning.domain.AllowedRoot;
import pl.pum.screensort.scanning.domain.SourcePathAccessException;
import pl.pum.screensort.scanning.domain.UnsafeSourcePathException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Component
public class LocalSourcePathResolver implements SourcePathResolver {

    private final Path userHome;
    private final List<Path> protectedLocations;

    public LocalSourcePathResolver() {
        this(currentUserHome(), currentProtectedLocations());
    }

    LocalSourcePathResolver(Path userHome, List<Path> protectedLocations) {
        this.userHome = canonicalizeConfiguredPath(Objects.requireNonNull(userHome));
        this.protectedLocations = protectedLocations.stream()
                .filter(Objects::nonNull)
                .map(LocalSourcePathResolver::canonicalizeConfiguredPath)
                .toList();
    }

    @Override
    public Path resolveAllowedRoot(Path requestedRoot) {
        Objects.requireNonNull(requestedRoot, "requestedRoot must not be null");
        Path canonicalRoot = toRealPath(requestedRoot);

        if (!Files.isDirectory(canonicalRoot)) {
            throw new UnsafeSourcePathException(requestedRoot, "the selected path is not a directory");
        }
        if (canonicalRoot.getParent() == null) {
            throw new UnsafeSourcePathException(requestedRoot, "filesystem roots cannot be selected");
        }
        if (canonicalRoot.equals(this.userHome)) {
            throw new UnsafeSourcePathException(requestedRoot, "the entire user home cannot be selected");
        }
        if (this.protectedLocations.stream().anyMatch(canonicalRoot::startsWith)) {
            throw new UnsafeSourcePathException(requestedRoot, "system locations cannot be selected");
        }

        return canonicalRoot;
    }

    @Override
    public Path resolveExistingFile(AllowedRoot allowedRoot, Path requestedFile) {
        Objects.requireNonNull(allowedRoot, "allowedRoot must not be null");
        Objects.requireNonNull(requestedFile, "requestedFile must not be null");

        Path candidate = requestedFile.isAbsolute()
                ? requestedFile
                : allowedRoot.canonicalPath().resolve(requestedFile);
        Path canonicalFile = toRealPath(candidate);

        if (!canonicalFile.startsWith(allowedRoot.canonicalPath())) {
            throw new UnsafeSourcePathException(requestedFile, "the resolved file is outside the allowed root");
        }
        if (!Files.isRegularFile(canonicalFile)) {
            throw new UnsafeSourcePathException(requestedFile, "the selected path is not a regular file");
        }

        return canonicalFile;
    }

    private static Path currentUserHome() {
        return Path.of(System.getProperty("user.home"));
    }

    private static List<Path> currentProtectedLocations() {
        return Stream.of(
                        System.getenv("SystemRoot"),
                        System.getenv("ProgramFiles"),
                        System.getenv("ProgramFiles(x86)"),
                        System.getenv("ProgramData"))
                .filter(value -> value != null && !value.isBlank())
                .map(Path::of)
                .toList();
    }

    private static Path canonicalizeConfiguredPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            return path.toAbsolutePath().normalize();
        }
    }

    private static Path toRealPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw new SourcePathAccessException(path, exception);
        }
    }

}
