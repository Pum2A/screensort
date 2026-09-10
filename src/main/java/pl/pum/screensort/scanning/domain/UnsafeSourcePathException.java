package pl.pum.screensort.scanning.domain;

import java.nio.file.Path;

public class UnsafeSourcePathException extends IllegalArgumentException {

    public UnsafeSourcePathException(Path path, String reason) {
        super("Unsafe source path '" + path + "': " + reason);
    }

}
