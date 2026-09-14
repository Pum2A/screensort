package pl.pum.screensort.scanning.validation;

import java.io.IOException;

public class InvalidScanFolderException extends IOException {

    public InvalidScanFolderException(String message) {
        super(message);
    }
}
