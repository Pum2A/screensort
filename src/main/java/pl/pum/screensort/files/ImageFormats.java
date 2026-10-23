package pl.pum.screensort.files;

import java.util.Locale;
import java.util.Set;

/** Which images the app reads: the same list for scanning, previews and file names. */
public final class ImageFormats {
  /** ImageIO reader format names, lowercase. */
  public static final Set<String> READABLE = Set.of("png", "jpeg", "jpg");
  private static final Set<String> EXTENSIONS = Set.of(".png", ".jpeg", ".jpg");

  private ImageFormats() {}

  public static boolean readable(String imageIoFormat) {
    return READABLE.contains(imageIoFormat.toLowerCase(Locale.ROOT));
  }

  public static boolean hasImageExtension(String fileName) {
    return EXTENSIONS.contains(FileNames.extension(fileName).toLowerCase(Locale.ROOT));
  }
}
