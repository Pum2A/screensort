package pl.pum.screensort.catalog.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.matches;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import java.util.Optional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import pl.pum.screensort.diagnostics.ProductMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import pl.pum.screensort.catalog.preview.CatalogImageSourceReader.ImageSource;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;

class CatalogImagePreviewTest {
  @TempDir Path folder;
  private CatalogImageSourceReader sourceReader;
  private CatalogImagePreview preview;

  @BeforeEach
  void setUp() {
    sourceReader = mock(CatalogImageSourceReader.class);
    preview = new CatalogImagePreview(sourceReader, new ScanFolderValidator(),
        new PreviewStore(Optional.empty(), new ProductMetrics(new SimpleMeterRegistry())));
  }

  @ParameterizedTest
  @ValueSource(strings = {"png", "jpeg"})
  void returnsBoundedPngWithoutChangingTheOriginal(String format) throws Exception {
    Path image = writeImage("photo." + format, format, 1920, 1080, Color.GREEN);
    byte[] original = Files.readAllBytes(image);
    FileTime modified = Files.getLastModifiedTime(image);
    source(image.getFileName().toString());

    BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(preview.read(1, CatalogImagePreview.Size.THUMBNAIL)));
    BufferedImage larger = ImageIO.read(new ByteArrayInputStream(preview.read(1, CatalogImagePreview.Size.PREVIEW)));
    assertThat(thumbnail.getWidth()).isEqualTo(320);
    assertThat(thumbnail.getHeight()).isEqualTo(180);
    assertThat(larger.getWidth()).isEqualTo(1024);
    assertThat(larger.getHeight()).isEqualTo(576);
    assertThat(Files.readAllBytes(image)).isEqualTo(original);
    assertThat(Files.getLastModifiedTime(image)).isEqualTo(modified);
    try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(preview.read(1, CatalogImagePreview.Size.THUMBNAIL)))) {
      var reader = ImageIO.getImageReaders(stream).next();
      try { assertThat(reader.getFormatName()).isEqualToIgnoringCase("png"); }
      finally { reader.dispose(); }
    }
  }

  @Test
  void doesNotUpscaleSmallImages() throws Exception {
    writeImage("small.png", "png", 8, 4, Color.RED);
    source("small.png");
    BufferedImage result = ImageIO.read(new ByteArrayInputStream(preview.read(1, CatalogImagePreview.Size.THUMBNAIL)));
    assertThat(result.getWidth()).isEqualTo(8);
    assertThat(result.getHeight()).isEqualTo(4);
  }

  @Test
  void preservesPngTransparency() throws Exception {
    var original = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
    original.setRGB(0, 0, 0x80ff0000);
    assertThat(ImageIO.write(original, "png", folder.resolve("transparent.png").toFile())).isTrue();
    source("transparent.png");
    BufferedImage result = ImageIO.read(new ByteArrayInputStream(preview.read(1, CatalogImagePreview.Size.THUMBNAIL)));
    assertThat(result.getRGB(0, 0)).isEqualTo(0x80ff0000);
    assertThat(result.getRGB(1, 1)).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"../outside.png", "..", ".", "nested/photo.png", "", "photo.png:secret"})
  void rejectsUnsafeStoredRelativePaths(String relative) throws Exception {
    source(relative);
    assertFailure(ImagePreviewException.Reason.UNSAFE_PATH);
  }

  @Test
  void rejectsAbsoluteStoredPath() throws Exception {
    Path image = writeImage("photo.png", "png", 2, 2, Color.RED);
    source(image.toString());
    assertFailure(ImagePreviewException.Reason.UNSAFE_PATH);
  }

  @Test
  void rejectsDirectoryMasqueradingAsImage() throws Exception {
    Files.createDirectory(folder.resolve("photo.png"));
    source("photo.png");
    assertFailure(ImagePreviewException.Reason.UNSAFE_PATH);
  }

  @Test
  void rejectsFakePng() throws Exception {
    Files.writeString(folder.resolve("fake.png"), "This is not an image.");
    source("fake.png");
    assertFailure(ImagePreviewException.Reason.INVALID_IMAGE);
  }

  @Test
  void rejectsUnsupportedActualFormatEvenWithPngExtension() throws Exception {
    writeImage("fake.png", "gif", 2, 2, Color.RED);
    source("fake.png");
    assertFailure(ImagePreviewException.Reason.INVALID_IMAGE);
  }

  @Test
  void rejectsEmptyAndOversizedFilesBeforeDecoding() throws Exception {
    Path image = Files.createFile(folder.resolve("photo.png"));
    source("photo.png");
    assertFailure(ImagePreviewException.Reason.TOO_LARGE);
    try (var sparseFile = new RandomAccessFile(image.toFile(), "rw")) {
      sparseFile.setLength(50L * 1024 * 1024 + 1);
    }
    assertFailure(ImagePreviewException.Reason.TOO_LARGE);
  }

  @ParameterizedTest
  @CsvSource({"100000,100000", "20001,1"})
  void rejectsHugePixelCountOrEdgeBeforeAllocatingTheImage(int width, int height) throws Exception {
    Path image = writeImage("photo.png", "png", 2, 2, Color.RED);
    byte[] png = Files.readAllBytes(image);
    ByteBuffer.wrap(png).putInt(16, width).putInt(20, height);
    CRC32 crc = new CRC32();
    crc.update(png, 12, 17);
    ByteBuffer.wrap(png).putInt(29, (int) crc.getValue());
    Files.write(image, png);
    source("photo.png");
    assertFailure(ImagePreviewException.Reason.TOO_LARGE);
  }

  @Test
  void cacheDoesNotHideRemovalOrReplacementByDirectory() throws Exception {
    Path image = writeImage("photo.png", "png", 2, 2, Color.RED);
    source("photo.png");
    preview.read(1, CatalogImagePreview.Size.THUMBNAIL);
    Files.delete(image);
    assertFailure(ImagePreviewException.Reason.NOT_FOUND);
    Files.createDirectory(image);
    assertFailure(ImagePreviewException.Reason.UNSAFE_PATH);
  }

  @Test
  void cacheDoesNotHideChangedContentWithNewMetadata() throws Exception {
    Path image = writeImage("photo.png", "png", 4, 4, Color.RED);
    source("photo.png");
    byte[] first = preview.read(1, CatalogImagePreview.Size.THUMBNAIL);
    FileTime time = Files.getLastModifiedTime(image);
    writeImage("photo.png", "png", 4, 4, Color.BLUE);
    Files.setLastModifiedTime(image, FileTime.from(time.toInstant().plusSeconds(1)));
    byte[] second = preview.read(1, CatalogImagePreview.Size.THUMBNAIL);
    assertThat(second).isNotEqualTo(first);
    BufferedImage result = ImageIO.read(new ByteArrayInputStream(second));
    assertThat(result.getRGB(0, 0)).isEqualTo(Color.BLUE.getRGB());
  }

  @Test
  void remoteCacheWaitCannotHideSourceRemovalAndItsKeyDoesNotExposeThePath() throws Exception {
    Path image = writeImage("private-photo.png", "png", 4, 4, Color.RED);
    source("private-photo.png");
    var store = mock(PreviewStore.class);
    when(store.get(anyString())).thenAnswer(call -> {
      Files.delete(image);
      return new byte[] {1, 2, 3};
    });
    preview = new CatalogImagePreview(sourceReader, new ScanFolderValidator(), store);
    assertFailure(ImagePreviewException.Reason.NOT_FOUND);
    verify(store).get(matches("screensort:preview:v2:[0-9a-f]{64}"));
  }

  // keepJfif=false is how phones write JPEG (Exif first); true is an edited photo (JFIF, then Exif).
  @ParameterizedTest
  @CsvSource({"6, false, true", "8, false, false", "6, true, true"})
  void phonePhotoIsTurnedUprightByItsExifOrientation(int orientation, boolean keepJfif, boolean redOnTop)
      throws Exception {
    // Stored lying down: 400x200, left half red, right half blue.
    var stored = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
    var graphics = stored.createGraphics();
    try {
      graphics.setColor(Color.RED); graphics.fillRect(0, 0, 200, 200);
      graphics.setColor(Color.BLUE); graphics.fillRect(200, 0, 200, 200);
    } finally { graphics.dispose(); }
    var jpeg = new java.io.ByteArrayOutputStream();
    assertThat(ImageIO.write(stored, "jpeg", jpeg)).isTrue();
    Files.write(folder.resolve("phone.jpg"), withExifOrientation(jpeg.toByteArray(), orientation, keepJfif));
    source("phone.jpg");

    BufferedImage result = ImageIO.read(new ByteArrayInputStream(preview.read(1, CatalogImagePreview.Size.THUMBNAIL)));

    assertThat(result.getWidth()).isEqualTo(160);
    assertThat(result.getHeight()).isEqualTo(320);
    Color top = new Color(result.getRGB(80, 40));
    Color bottom = new Color(result.getRGB(80, 280));
    assertThat(top.getRed() > top.getBlue()).isEqualTo(redOnTop);
    assertThat(bottom.getRed() > bottom.getBlue()).isEqualTo(!redOnTop);
  }

  // Adds an APP1 Exif block (big-endian TIFF, one entry: Orientation). The JDK writer starts with
  // SOI and a JFIF APP0 block; the Exif block goes after APP0, or replaces it like a phone camera.
  private static byte[] withExifOrientation(byte[] jpeg, int orientation, boolean keepJfif) {
    byte[] tiff = {
        'M', 'M', 0, 42, 0, 0, 0, 8,
        0, 1,
        0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0,
        0, 0, 0, 0};
    byte[] header = "Exif\0\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    int length = 2 + header.length + tiff.length;
    int afterJfif = 4 + (((jpeg[4] & 0xff) << 8) | (jpeg[5] & 0xff));
    int head = keepJfif ? afterJfif : 2;
    var out = new java.io.ByteArrayOutputStream();
    out.write(jpeg, 0, head);
    out.write(0xFF);
    out.write(0xE1);
    out.write(length >> 8);
    out.write(length & 0xff);
    out.writeBytes(header);
    out.writeBytes(tiff);
    out.write(jpeg, afterJfif, jpeg.length - afterJfif);
    return out.toByteArray();
  }

  @Test
  void rejectsSymbolicLinkWhenThePlatformAllowsCreatingOne() throws Exception {
    Path image = writeImage("original.png", "png", 2, 2, Color.RED);
    try {
      Files.createSymbolicLink(folder.resolve("link.png"), image);
    } catch (java.io.IOException | UnsupportedOperationException e) {
      org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable: " + e.getClass().getSimpleName());
    }
    source("link.png");
    assertFailure(ImagePreviewException.Reason.UNSAFE_PATH);
  }

  @Test
  void aWholeGalleryPageRequestedAtOnceGetsEveryThumbnail() throws Exception {
    int page = 24;
    for (int id = 1; id <= page; id++) {
      writeImage("page-" + id + ".png", "png", 1920, 1080, new Color(id * 10, 80, 160));
      when(sourceReader.read(id)).thenReturn(
          new ImageSource(id, folder.toRealPath().toString(), "page-" + id + ".png", "0".repeat(64)));
    }
    var gallery = java.util.concurrent.Executors.newFixedThreadPool(page);
    try {
      var requests = new java.util.ArrayList<java.util.concurrent.Callable<byte[]>>();
      for (int id = 1; id <= page; id++) {
        long fileId = id;
        requests.add(() -> preview.read(fileId, CatalogImagePreview.Size.THUMBNAIL));
      }
      // Wiring check for a full page; the deterministic wait is covered by PreviewDecodeQueueTest.
      for (var thumbnail : gallery.invokeAll(requests)) {
        assertThat(ImageIO.read(new ByteArrayInputStream(thumbnail.get())).getWidth()).isEqualTo(320);
      }
    } finally {
      gallery.shutdownNow();
    }
  }

  private void source(String relative) throws Exception {
    when(sourceReader.read(1)).thenReturn(new ImageSource(1, folder.toRealPath().toString(), relative, "0".repeat(64)));
  }

  private void assertFailure(ImagePreviewException.Reason reason) {
    assertThatThrownBy(() -> preview.read(1, CatalogImagePreview.Size.THUMBNAIL))
        .isInstanceOfSatisfying(ImagePreviewException.class, error -> assertThat(error.reason()).isEqualTo(reason));
  }

  private Path writeImage(String name, String format, int width, int height, Color color) throws Exception {
    var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    try { graphics.setColor(color); graphics.fillRect(0, 0, width, height); }
    finally { graphics.dispose(); }
    Path path = folder.resolve(name);
    assertThat(ImageIO.write(image, format, path.toFile())).isTrue();
    return path;
  }
}
