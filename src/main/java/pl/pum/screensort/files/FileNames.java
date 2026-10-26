package pl.pum.screensort.files;

/** File name parts, the same everywhere a name is checked, proposed or copied. */
public final class FileNames {
  private FileNames() {}

  /**
   * The last ".xyz" of a name, with the dot and in its original case; "" when there is none.
   * A leading dot is part of the name, so ".hidden" has no extension.
   */
  public static String extension(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 && dot < name.length() - 1 ? name.substring(dot) : "";
  }
}
