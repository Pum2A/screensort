package pl.pum.screensort.analysis.source;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.READ;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.catalog.preview.CatalogImageSourceReader;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;

@Service
public class AnalysisSourceReader {
  private static final int MAX_BYTES = 50 * 1024 * 1024;
  private final CatalogImageSourceReader sources;
  private final ScanFolderValidator folders;

  public AnalysisSourceReader(CatalogImageSourceReader sources, ScanFolderValidator folders) {
    this.sources = sources;
    this.folders = folders;
  }

  public record Input(long fileId, byte[] bytes, String sha256, String mediaType) {}

  public Input read(long fileId) {
    var source = sources.read(fileId);
    try {
      Path storedRoot = Path.of(source.folderPath());
      Path root = folders.validate(storedRoot).path();
      Path relative = Path.of(source.relativePath());
      if (!root.equals(storedRoot) || relative.isAbsolute() || relative.getNameCount() != 1
          || source.relativePath().contains(":") || relative.toString().isBlank()
          || relative.toString().equals(".") || relative.toString().equals("..")) {
        throw new AnalysisException(400, "Niedozwolona lokalizacja obrazu.");
      }
      Path path = root.resolve(relative).normalize();
      if (!root.equals(path.getParent()) || !path.equals(path.toRealPath())) {
        throw new AnalysisException(400, "Nie analizujemy przekierowanych ścieżek.");
      }
      BasicFileAttributes before = attributes(path);
      byte[] bytes;
      try (var channel = Files.newByteChannel(path, Set.of(READ, NOFOLLOW_LINKS));
          var input = Channels.newInputStream(channel)) {
        bytes = input.readNBytes(MAX_BYTES + 1);
      }
      BasicFileAttributes after = attributes(path);
      if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new AnalysisException(413, "Limit wejścia: 50 MiB.");
      if (before.size() != bytes.length || before.size() != after.size()
          || !before.lastModifiedTime().equals(after.lastModifiedTime())
          || !Objects.equals(before.fileKey(), after.fileKey())
          || !path.equals(path.toRealPath()) || !root.equals(folders.validate(root).path())) {
        throw new AnalysisException(409, "Plik zmienił się podczas odczytu. Spróbuj ponownie.");
      }
      String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      String media = bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P'
          ? "image/png" : "image/jpeg";
      return new Input(fileId, bytes, hash, media);
    } catch (IOException e) {
      throw new AnalysisException(404, "Plik nie jest dostępny w wybranym folderze.");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Required SHA-256 is unavailable", e);
    }
  }

  private BasicFileAttributes attributes(Path path) throws IOException {
    var attrs = Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS);
    if (!attrs.isRegularFile() || attrs.isSymbolicLink()) throw new AnalysisException(400, "Obraz musi być zwykłym plikiem.");
    if (attrs.size() <= 0 || attrs.size() > MAX_BYTES) throw new AnalysisException(413, "Limit wejścia: 50 MiB.");
    return attrs;
  }
}
