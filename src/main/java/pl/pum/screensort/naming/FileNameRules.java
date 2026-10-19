package pl.pum.screensort.naming;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Rules for one filename, never a destination path or permission to write a file. */
public final class FileNameRules {
  private static final Pattern FORBIDDEN = Pattern.compile("[<>:\"/\\\\|?*\\p{C}]");
  private static final Pattern DEVICE = Pattern.compile(
      "(?iu)^(con|prn|aux|nul|clock\\$|conin\\$|conout\\$|com[1-9¹²³]|lpt[1-9¹²³])$");

  private FileNameRules() {}

  public static Optional<String> problem(String name, String originalName) {
    if (name == null || name.isBlank()) return Optional.of("Wpisz nazwę pliku.");
    if (name.length() > 255 || name.getBytes(StandardCharsets.UTF_8).length > 255) {
      return Optional.of("Nazwa może mieć najwyżej 255 znaków i 255 bajtów UTF-8.");
    }
    if (!name.equals(name.strip()) || name.endsWith(".")) {
      return Optional.of("Nazwa nie może zaczynać się ani kończyć spacją lub kończyć kropką.");
    }
    if (FORBIDDEN.matcher(name).find()) {
      return Optional.of("Wpisz samą nazwę, bez ścieżki i znaków niedozwolonych w Windows.");
    }
    String devicePart = name.split("\\.", 2)[0].stripTrailing();
    if (DEVICE.matcher(devicePart).matches()) {
      return Optional.of("Ta nazwa jest zarezerwowana przez Windows.");
    }
    String extension = extension(originalName);
    if (extension.isEmpty() || name.length() <= extension.length()
        || !name.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT))
        || name.substring(0, name.length() - extension.length()).matches("[. ]+")) {
      return Optional.of("Zachowaj oryginalne rozszerzenie i wpisz nazwę przed nim.");
    }
    return Optional.empty();
  }

  public static String extension(String originalName) {
    int dot = originalName.lastIndexOf('.');
    return dot > 0 && dot < originalName.length() - 1 ? originalName.substring(dot) : "";
  }
}
