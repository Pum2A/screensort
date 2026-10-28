package pl.pum.screensort.gallery.preview;

import pl.pum.screensort.files.SourcePath;
import pl.pum.screensort.files.Sha256;
import pl.pum.screensort.files.ImageFormats;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.READ;
import static pl.pum.screensort.gallery.preview.ImagePreviewException.Reason.BUSY;
import static pl.pum.screensort.gallery.preview.ImagePreviewException.Reason.CHANGED;
import static pl.pum.screensort.gallery.preview.ImagePreviewException.Reason.INVALID_IMAGE;
import static pl.pum.screensort.gallery.preview.ImagePreviewException.Reason.NOT_FOUND;
import static pl.pum.screensort.gallery.preview.ImagePreviewException.Reason.TOO_LARGE;
import static pl.pum.screensort.gallery.preview.ImagePreviewException.Reason.UNSAFE_PATH;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Service;
import pl.pum.screensort.gallery.preview.CatalogImageSourceReader.ImageSource;
import pl.pum.screensort.gallery.reading.InvalidScanFolderException;
import pl.pum.screensort.gallery.reading.ScanFolderValidator;

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
  private final PreviewStore cache;
  // Two decoders bound memory; others queue. The wait stays below the desktop shell's 12 s
  // request timeout, so a queued thumbnail is answered instead of silently dropped.
  private final PreviewDecodeQueue decoders = new PreviewDecodeQueue(2, Duration.ofSeconds(10));

  public CatalogImagePreview(CatalogImageSourceReader sourceReader, ScanFolderValidator folderValidator,
      PreviewStore cache) {
    this.sourceReader = sourceReader;
    this.folderValidator = folderValidator;
    this.cache = cache;
  }

  public byte[] read(long fileId, Size size) {
    ImageSource source = sourceReader.read(fileId);
    try {
      Path path = resolveSafePath(source);
      BasicFileAttributes before = readAttributes(path);
      String key = new CacheKey(fileId, path, size, before.size(),
          before.lastModifiedTime().toInstant(), before.fileKey(), source.sha256()).redisKey();
      // Nawet trafienie w cache wymaga aktualnego sprawdzenia folderu i pliku.
      byte[] cached = cache.get(key);
      if (cached != null) {
        if (!path.equals(resolveSafePath(source)) || !sameFile(before, readAttributes(path))) {
          throw new ImagePreviewException(CHANGED, "Plik zmienił się podczas odczytu podglądu.");
        }
        return cached;
      }

      return decoders.decode(key, () -> {
        // Sprawdzamy ponownie po oczekiwaniu na wolny dekoder.
        if (!path.equals(resolveSafePath(source)) || !sameFile(before, readAttributes(path))) {
          throw new ImagePreviewException(CHANGED, "Plik zmienił się. Odśwież katalog.");
        }
        // W trakcie czekania inne żądanie mogło już wygenerować ten sam podgląd.
        byte[] ready = cache.get(key);
        if (ready != null) return ready;
        byte[] png = decode(path, size.edge);
        if (!path.equals(resolveSafePath(source)) || !sameFile(before, readAttributes(path))) {
          throw new ImagePreviewException(CHANGED, "Plik zmienił się podczas odczytu.");
        }
        cache.put(key, png);
        return png;
      });
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
    Path path = SourcePath.directChild(root, source.relativePath())
        .orElseThrow(() -> new ImagePreviewException(UNSAFE_PATH, "Niedozwolona ścieżka obrazu."));
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
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!ImageFormats.readable(format)) {
          throw new ImagePreviewException(INVALID_IMAGE, "Podgląd obsługuje PNG i JPEG.");
        }
        boolean jpeg = !format.equals("png");
        // Only JPEG metadata is read: it carries the EXIF orientation of phone photos.
        reader.setInput(images, true, !jpeg);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width <= 0 || height <= 0 || width > MAX_SOURCE_EDGE || height > MAX_SOURCE_EDGE
            || (long) width * height > MAX_SOURCE_PIXELS) {
          throw new ImagePreviewException(TOO_LARGE, "Limit podglądu: 40 mln pikseli i 20 000 px na bok.");
        }
        var params = reader.getDefaultReadParam();
        int sample = Math.max(1, Math.max(width, height) / edge);
        params.setSourceSubsampling(sample, sample, 0, 0);
        int orientation = jpeg ? orientation(reader) : ExifOrientation.NORMAL;
        BufferedImage decoded = reader.read(0, params);
        try {
          return encodeScaledPng(decoded, edge, orientation);
        } finally {
          decoded.flush();
        }
      } finally {
        reader.dispose();
      }
    }
  }

  // A broken EXIF block must not cost the preview: the photo is then shown as stored.
  private static int orientation(ImageReader reader) {
    try {
      return ExifOrientation.read(reader.getImageMetadata(0));
    } catch (IOException | RuntimeException e) {
      return ExifOrientation.NORMAL;
    }
  }

  private byte[] encodeScaledPng(BufferedImage decoded, int edge, int orientation) throws IOException {
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
    // Turned after scaling: the small image is cheap to rotate, and its longer edge stays within `edge`.
    BufferedImage upright = ExifOrientation.apply(scaled, orientation);
    try (var output = new ByteArrayOutputStream()) {
      if (!ImageIO.write(upright, "png", output)) throw new IOException("PNG writer unavailable");
      return output.toByteArray();
    } finally {
      scaled.flush();
      upright.flush();
    }
  }

  private record CacheKey(long fileId, Path path, Size size, long bytes,
      Instant modifiedAt, Object fileKey, String sha256) {
    String redisKey() {
      // NUL separates fields unambiguously; keys never expose source paths or filenames.
      String value = fileId + "\0" + path + "\0" + size + "\0" + bytes + "\0" + modifiedAt
          + "\0" + Objects.toString(fileKey, "") + "\0" + sha256;
      // v2: previews follow EXIF orientation. Old v1 entries are never read again.
      return "screensort:preview:v2:" + Sha256.hex(value.getBytes(StandardCharsets.UTF_8));
    }
  }

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
