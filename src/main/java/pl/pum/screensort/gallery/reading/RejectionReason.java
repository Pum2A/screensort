package pl.pum.screensort.gallery.reading;

public enum RejectionReason {
    UNREADABLE_IMAGE,
    TOO_LARGE,
    NOT_A_REGULAR_FILE,
    UNSUPPORTED_EXTENSION,
    SYMBOLIC_LINK,
    METADATA_UNREADABLE
}
