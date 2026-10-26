package pl.pum.screensort.files;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The one place that creates SHA-256 digests and formats them as lowercase hex. */
public final class Sha256 {
  private static final int BUFFER_BYTES = 64 * 1024;

  private Sha256() {}

  /** Every Java runtime must ship SHA-256, so a missing one is a broken installation, not a user error. */
  public static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("Required SHA-256 is unavailable", error);
    }
  }

  public static String hex(MessageDigest digest) {
    return HexFormat.of().formatHex(digest.digest());
  }

  public static String hex(byte[] data) {
    return HexFormat.of().formatHex(digest().digest(data));
  }

  /** Streams the input, so large files never sit in memory as a whole. */
  public static String hex(InputStream input) throws IOException {
    MessageDigest digest = digest();
    byte[] buffer = new byte[BUFFER_BYTES];
    int read;
    while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
    return hex(digest);
  }
}
