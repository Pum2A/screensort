package pl.pum.screensort.scanning.application.port;

import pl.pum.screensort.scanning.domain.AllowedRoot;

import java.nio.file.Path;

public interface SourcePathResolver {

    Path resolveAllowedRoot(Path requestedRoot);

    Path resolveExistingFile(AllowedRoot allowedRoot, Path requestedFile);

}
