package pl.pum.screensort.gallery.reading;


public record RejectedFileDto(String path, String originalName, RejectionReason reason) {
  public static RejectedFileDto from(RejectedFile source) {
    return new RejectedFileDto(source.path().toString(), source.originalName(), source.reason());
  }
}
