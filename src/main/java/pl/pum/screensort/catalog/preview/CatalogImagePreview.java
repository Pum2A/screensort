package pl.pum.screensort.catalog.preview;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.READ;
import static pl.pum.screensort.catalog.preview.ImagePreviewException.Reason.BUSY;
import static pl.pum.screensort.catalog.preview.ImagePreviewException.Reason.CHANGED;
import static pl.pum.screensort.catalog.preview.ImagePreviewException.Reason.INVALID_IMAGE;
import static pl.pum.screensort.catalog.preview.ImagePreviewException.Reason.NOT_FOUND;
import static pl.pum.screensort.catalog.preview.ImagePreviewException.Reason.TOO_LARGE;
import static pl.pum.screensort.catalog.preview.ImagePreviewException.Reason.UNSAFE_PATH;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Service;
import pl.pum.screensort.catalog.preview.CatalogImageSourceReader.ImageSource;
import pl.pum.screensort.scanning.folder.InvalidScanFolderException;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;

@Service
public class CatalogImagePreview {
  public enum Size {
    THUMBNAIL(320), PREVIEW(1024);
    private final int edge;
    Size(int edge) { this.edge = edge; }
  }

  private static final long MAX_SOURCE_BYTES = 50L * 1024 * 1024;
  private static final long MAX_SOURCE_PIXELS = 40_000_000;
  private static final int MAX_SOURCE_EDGE = 20_000;
  private final CatalogImageSourceReader sourceReader;
  private final ScanFolderValidator folderValidator;
  private final PreviewCache cache = new PreviewCache(128, 16L * 1024 * 1024);
  private final Semaphore decoders = new Semaphore(2);

  public CatalogImagePreview(CatalogImageSourceReader sourceReader, ScanFolderValidator folderValidator) {
    this.sourceReader = sourceReader;
    this.folderValidator = folderValidator;
  }

  public byte[] read(long fileId, Size size) {
    ImageSource source = sourceReader.read(fileId);
    try {
      Path path = resolveSafePath(source);
      BasicFileAttributes before = readAttributes(path);
      CacheKey key = new CacheKey(fileId, path, size, before.size(),
          before.lastModifiedTime().toInstant(), before.fileKey(), source.sha256());
      // Nawet trafienie w cache wymaga aktualnego sprawdzenia folderu i pliku.
      byte[] cached = cache.get(key);
      if (cached != null) return cached;

      if (!decoders.tryAcquire(2, TimeUnit.SECONDS)) {
        throw new ImagePreviewException(BUSY, "Podglądy są zajęte. Spróbuj ponownie za chwilę.");
      }
      try {
        // Sprawdzamy ponownie po oczekiwaniu na wolny dekoder.
        if (!path.equals(resolveSafePath(source)) || !sameFile(before, readAttributes(path))) {
          throw new ImagePreviewException(CHANGED, "Plik zmienił się. Odśwież katalog.");
        }
        byte[] png = decode(path, size.edge);
        if (!path.equals(resolveSafePath(source)) || !sameFile(before, readAttributes(path))) {
          throw new ImagePreviewException(CHANGED, "Plik zmienił się podczas odczytu.");
        }
        cache.put(key, png);
        return png;
      } finally {
        decoders.release();
      }
    } catch (NoSuchFileException e) {
      throw new ImagePreviewException(NOT_FOUND, "Plik nie jest już dostępny w folderze.");
    } catch (InvalidScanFolderException | InvalidPathException e) {
      throw new ImagePreviewException(UNSAFE_PATH, "Ścieżka obrazu nie jest dozwolona.");
    } catch (IOException e) {
      throw new ImagePreviewException(INVALID_IMAGE, "Nie udało się odczytać podglądu obrazu.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ImagePreviewException(BUSY, "Odczyt podglądu został przerwany.");
    }
  }

  private Path resolveSafePath(ImageSource source) throws IOException {
    Path storedRoot = Path.of(source.folderPath());
    Path root = folderValidator.validate(storedRoot).path();
    if (!root.equals(storedRoot)) {
      throw new ImagePreviewException(UNSAFE_PATH, "Folder źródłowy zmienił położenie.");
    }
    Path relative = Path.of(source.relativePath());
    // Obecny skaner jest nierekurencyjny: akceptujemy tylko bezpośrednie pliki folderu.
    if (relative.isAbsolute() || relative.getNameCount() != 1
        || source.relativePath().contains(":") || relative.toString().isBlank()
        || relative.toString().equals(".") || relative.toString().equals("..")) {
      throw new ImagePreviewException(UNSAFE_PATH, "Niedozwolona ścieżka obrazu.");
    }
    Path path = root.resolve(relative).normalize();
    if (!root.equals(path.getParent())) {
      throw new ImagePreviewException(UNSAFE_PATH, "Obraz musi należeć do wybranego folderu.");
    }
    readAttributes(path);
    if (!path.equals(path.toRealPath())) {
      throw new ImagePreviewException(UNSAFE_PATH, "Nie otwieramy przekierowanych ścieżek.");
    }
    return path;
  }

  private BasicFileAttributes readAttributes(Path path) throws IOException {
    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS);
    if (attrs.isSymbolicLink() || !attrs.isRegularFile()) {
      throw new ImagePreviewException(UNSAFE_PATH, "Podgląd wymaga zwykłego pliku, nie linku ani folderu.");
    }
    if (attrs.size() <= 0 || attrs.size() > MAX_SOURCE_BYTES) {
      throw new ImagePreviewException(TOO_LARGE, "Obraz musi mieć od 1 bajta do 50 MiB.");
    }
    return attrs;
  }

  private boolean sameFile(BasicFileAttributes before, BasicFileAttributes after) {
    return before.size() == after.size() && before.lastModifiedTime().equals(after.lastModifiedTime())
        && Objects.equals(before.fileKey(), after.fileKey());
  }

  private byte[] decode(Path path, int edge) throws IOException {
    try (var channel = Files.newByteChannel(path, Set.of(READ, NOFOLLOW_LINKS));
        InputStream input = new LimitedInputStream(Channels.newInputStream(channel));
        var images = new MemoryCacheImageInputStream(input)) {
      var readers = ImageIO.getImageReaders(images);
      if (!readers.hasNext()) {
        throw new ImagePreviewException(INVALID_IMAGE, "Plik nie jest obsługiwanym obrazem.");
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(images, true, true);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!Set.of("png", "jpeg", "jpg").contains(format)) {
          throw new ImagePreviewException(INVALID_IMAGE, "Podgląd obsługuje PNG i JPEG.");
        }
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width <= 0 || height <= 0 || width > MAX_SOURCE_EDGE || height > MAX_SOURCE_EDGE
            || (long) width * height > MAX_SOURCE_PIXELS) {
          throw new ImagePreviewException(TOO_LARGE, "Limit podglądu: 40 mln pikseli i 20 000 px na bok.");
        }
        var params = reader.getDefaultReadParam();
        int sample = Math.max(1, Math.max(width, height) / edge);
        params.setSourceSubsampling(sample, sample, 0, 0);
        BufferedImage decoded = reader.read(0, params);
        try {
          return encodeScaledPng(decoded, edge);
        } finally {
          decoded.flush();
        }
      } finally {
        reader.dispose();
      }
    }
  }

  private byte[] encodeScaledPng(BufferedImage decoded, int edge) throws IOException {
    double scale = Math.min(1.0, (double) edge / Math.max(decoded.getWidth(), decoded.getHeight()));
    int width = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
    int height = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
    BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = scaled.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.drawImage(decoded, 0, 0, width, height, null);
    } finally {
      graphics.dispose();
    }
    try (var output = new ByteArrayOutputStream()) {
      if (!ImageIO.write(scaled, "png", output)) throw new IOException("PNG writer unavailable");
      return output.toByteArray();
    } finally {
      scaled.flush();
    }
  }

  private record CacheKey(long fileId, Path path, Size size, long bytes,
      Instant modifiedAt, Object fileKey, String sha256) {}

  // Plik może urosnąć po sprawdzeniu metadanych: również sam odczyt ma limit bajtów.
  private static final class LimitedInputStream extends FilterInputStream {
    private long readBytes;
    LimitedInputStream(InputStream input) { super(input); }

    @Override
    public int read() throws IOException {
      int value = in.read();
      if (value != -1) checkLimit(1);
      return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      int allowed = (int) Math.min(length, MAX_SOURCE_BYTES - readBytes + 1);
      int count = in.read(bytes, offset, allowed);
      if (count > 0) checkLimit(count);
      return count;
    }

    private void checkLimit(int count) {
      readBytes += count;
      if (readBytes > MAX_SOURCE_BYTES) throw new ImagePreviewException(TOO_LARGE, "Plik przekroczył 50 MiB.");
    }
  }
}
