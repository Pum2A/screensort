package pl.pum.screensort.scanning.domain;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A source directory that has passed the filesystem safety policy.
 */
public record AllowedRoot(Path canonicalPath, boolean recursiveScan) {

    public AllowedRoot {
        canonicalPath = Objects.requireNonNull(canonicalPath, "canonicalPath must not be null").normalize();
        if (!canonicalPath.isAbsolute()) {
            throw new IllegalArgumentException("canonicalPath must be absolute");
        }
    }

}
