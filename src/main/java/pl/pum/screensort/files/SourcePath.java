package pl.pum.screensort.files;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The one rule for where a catalog file may live: directly inside its validated folder, named by a
 * plain file name. The scanner is not recursive, so anything else is a stored path we do not open.
 * Callers still check {@code toRealPath()} and attributes themselves, at the moment they read.
 */
public final class SourcePath {
  private SourcePath() {}

  public static Optional<Path> directChild(Path root, String relativePath) {
    if (relativePath == null || relativePath.isBlank() || relativePath.contains(":")) return Optional.empty();
    Path relative;
    try {
      relative = Path.of(relativePath);
    } catch (InvalidPathException error) {
      return Optional.empty();
    }
    String name = relative.toString();
    if (relative.isAbsolute() || relative.getNameCount() != 1 || name.equals(".") || name.equals("..")) {
      return Optional.empty();
    }
    Path path = root.resolve(relative).normalize();
    return root.equals(path.getParent()) ? Optional.of(path) : Optional.empty();
  }
}
