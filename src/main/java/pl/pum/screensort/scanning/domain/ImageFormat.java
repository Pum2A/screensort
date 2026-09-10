package pl.pum.screensort.scanning.domain;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Image formats that ScreenSort accepts during source discovery.
 */
public enum ImageFormat {

    PNG("image/png"),
    JPEG("image/jpeg");

    private final String mediaType;

    ImageFormat(String mediaType) {
        this.mediaType = mediaType;
    }

    public String mediaType() {
        return this.mediaType;
    }

    public static Optional<ImageFormat> fromPath(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        Path fileName = path.getFileName();
        if (fileName == null) {
            return Optional.empty();
        }

        String normalizedName = fileName.toString().toLowerCase(Locale.ROOT);
        if (normalizedName.endsWith(".png")) {
            return Optional.of(PNG);
        }
        if (normalizedName.endsWith(".jpg") || normalizedName.endsWith(".jpeg")) {
            return Optional.of(JPEG);
        }
        return Optional.empty();
    }

}
