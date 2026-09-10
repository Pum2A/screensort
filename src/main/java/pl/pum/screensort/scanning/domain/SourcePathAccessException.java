package pl.pum.screensort.scanning.domain;

import java.nio.file.Path;

public class SourcePathAccessException extends RuntimeException {

    public SourcePathAccessException(Path path, Throwable cause) {
        super("Cannot resolve source path: " + path, cause);
    }

}
