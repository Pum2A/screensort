package pl.pum.screensort.gallery.reading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageScannerTest {

  @Test
  void corruptImageHeaderRejectsOnlyThatFileAndKeepsScanning(@TempDir Path folder) throws Exception {
    writeImage(folder.resolve("good.png"), 2);
    byte[] pngSignature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    byte[] corrupt = new byte[64];
    java.util.Arrays.fill(corrupt, (byte) 0xFF);
    System.arraycopy(pngSignature, 0, corrupt, 0, pngSignature.length);
    Files.write(folder.resolve("corrupt.png"), corrupt);

    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      ImageScanner scanner = new ImageScanner(new FileHasher(), workers);

      ScanResult result = scanner.scan(new ScanFolderValidator().validate(folder), Map.of());

      assertEquals(1, result.accepted().size());
      assertEquals(1, result.rejected().size());
      assertEquals(RejectionReason.UNREADABLE_IMAGE, result.rejected().getFirst().reason());
    }
  }

  @Test
  void findsSupportedImagesAndRejectsUnsupportedFile(@TempDir Path folder) throws Exception {
    writeImage(folder.resolve("first.png"), 2);
    writeImage(folder.resolve("second.png"), 3);
    Files.writeString(folder.resolve("notes.zip"), "Synthetic unsupported-file fixture.");

    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      ImageScanner scanner = new ImageScanner(new FileHasher(), workers);
      AllowedRoot allowedRoot = new ScanFolderValidator().validate(folder);

      ScanResult result = scanner.scan(allowedRoot, Map.of());

      assertEquals(2, result.accepted().size()); // dwa PNG przeszły
      assertEquals(1, result.rejected().size()); // ZIP trafił do odrzuconych
      assertEquals(new ScanSummary(2, 0, 0, 1), result.summary());
      for (AcceptedImage image : result.accepted()) {
        assertEquals(Files.size(image.path()), image.sizeBytes());
        assertEquals(
            Files.getLastModifiedTime(image.path()).toInstant(), image.lastModifiedAt());
      }
    }
  }

  @Test
  void rejectsFileWithImageExtensionButUnreadableContents(@TempDir Path folder) throws Exception {
    Files.writeString(folder.resolve("not-an-image.png"), "This is text, not an image.");

    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      ImageScanner scanner = new ImageScanner(new FileHasher(), workers);
      AllowedRoot allowedRoot = new ScanFolderValidator().validate(folder);

      ScanResult result = scanner.scan(allowedRoot, Map.of());

      assertEquals(0, result.accepted().size());
      assertEquals(1, result.rejected().size());
      assertEquals(RejectionReason.UNREADABLE_IMAGE, result.rejected().getFirst().reason());
      assertEquals(new ScanSummary(0, 0, 0, 1), result.summary());
    }
  }

  @Test
  void rejectsGifContentBehindPngName(@TempDir Path folder) throws Exception {
    Path path = folder.resolve("mislabelled.png");
    assertTrue(
        ImageIO.write(
            new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "gif", path.toFile()));

    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      ImageScanner scanner = new ImageScanner(new FileHasher(), workers);
      AllowedRoot allowedRoot = new ScanFolderValidator().validate(folder);

      ScanResult result = scanner.scan(allowedRoot, Map.of());

      // Podgląd obsługuje PNG i JPEG, więc GIF pod nazwą .png nie wchodzi do katalogu.
      assertEquals(0, result.accepted().size());
      assertEquals(1, result.rejected().size());
      assertEquals(RejectionReason.UNREADABLE_IMAGE, result.rejected().getFirst().reason());
    }
  }

  @Test
  void rejectsImageBeyondPreviewLimitsWithoutDecodingIt(@TempDir Path folder) throws Exception {
    Path path = folder.resolve("huge.png");
    writeImage(path, 2);
    byte[] png = Files.readAllBytes(path);
    ByteBuffer.wrap(png).putInt(16, 100_000).putInt(20, 100_000);
    CRC32 crc = new CRC32();
    crc.update(png, 12, 17);
    ByteBuffer.wrap(png).putInt(29, (int) crc.getValue());
    Files.write(path, png);

    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      ImageScanner scanner = new ImageScanner(new FileHasher(), workers);
      AllowedRoot allowedRoot = new ScanFolderValidator().validate(folder);

      ScanResult result = scanner.scan(allowedRoot, Map.of());

      // Nagłówek deklaruje 10 mln pikseli: skan odrzuca plik, zanim cokolwiek zdekoduje.
      assertEquals(0, result.accepted().size());
      assertEquals(1, result.rejected().size());
      assertEquals(RejectionReason.UNREADABLE_IMAGE, result.rejected().getFirst().reason());
    }
  }

  @Test
  void distinguishesNewRescannedAndUnchangedImages(@TempDir Path folder) throws Exception {
    Path imagePath = folder.resolve("photo.png");
    writeImage(imagePath, 2);
    FileHasher hasher = spy(new FileHasher());

    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      ImageScanner scanner = new ImageScanner(hasher, workers);
      AllowedRoot allowedRoot = new ScanFolderValidator().validate(folder);

      // Pierwszy skan: plik jest nowy i naprawdę obliczamy hash.
      ScanResult firstScan = scanner.scan(allowedRoot, Map.of());
      assertEquals(new ScanSummary(1, 0, 0, 0), firstScan.summary());
      verify(hasher).hash(imagePath);
      AcceptedImage firstImage = firstScan.accepted().getFirst();
      Map<String, KnownFile> knownFiles = Map.of("photo.png", snapshot(firstImage));
      clearInvocations(hasher);

      // Drugi skan: te same metadane, więc hasher nie może być wywołany.
      ScanResult unchangedScan = scanner.scan(allowedRoot, knownFiles);
      assertEquals(new ScanSummary(0, 0, 1, 0), unchangedScan.summary());
      assertEquals(firstImage.hash(), unchangedScan.accepted().getFirst().hash());
      verify(hasher, never()).hash(any(Path.class));

      // Zmiana samej daty też wymaga pełnego przetwarzania znanego obrazu.
      Files.setLastModifiedTime(
          imagePath, FileTime.from(firstImage.lastModifiedAt().plusSeconds(60)));
      ScanResult rescanned = scanner.scan(allowedRoot, knownFiles);
      assertEquals(new ScanSummary(0, 1, 0, 0), rescanned.summary());
      assertEquals(firstImage.hash(), rescanned.accepted().getFirst().hash());
      verify(hasher).hash(imagePath);
      knownFiles = Map.of("photo.png", snapshot(rescanned.accepted().getFirst()));
      clearInvocations(hasher);

      // Mieszany skan: nowy plik jest hashowany, znany pozostaje na szybkiej ścieżce.
      Path newImagePath = folder.resolve("new-photo.png");
      writeImage(newImagePath, 3);
      ScanResult mixedScan = scanner.scan(allowedRoot, knownFiles);
      assertEquals(new ScanSummary(1, 0, 1, 0), mixedScan.summary());
      assertEquals(2, mixedScan.accepted().size());
      verify(hasher).hash(newImagePath);
      verify(hasher, never()).hash(imagePath);
    }
  }

  @Test
  void rescansWhenSizeChangesEvenIfModificationTimeMatches(@TempDir Path folder) throws Exception {
    Path imagePath = folder.resolve("photo.png");
    writeImage(imagePath, 2);
    FileHasher hasher = spy(new FileHasher());

    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      ImageScanner scanner = new ImageScanner(hasher, workers);
      AllowedRoot allowedRoot = new ScanFolderValidator().validate(folder);
      AcceptedImage firstImage = scanner.scan(allowedRoot, Map.of()).accepted().getFirst();
      Map<String, KnownFile> knownFiles = Map.of("photo.png", snapshot(firstImage));
      clearInvocations(hasher);

      writeImage(imagePath, 32);
      Files.setLastModifiedTime(imagePath, FileTime.from(firstImage.lastModifiedAt()));
      assertNotEquals(firstImage.sizeBytes(), Files.size(imagePath));

      ScanResult result = scanner.scan(allowedRoot, knownFiles);
      assertEquals(new ScanSummary(0, 1, 0, 0), result.summary());
      assertNotEquals(firstImage.hash(), result.accepted().getFirst().hash());
      verify(hasher).hash(imagePath);
    }
  }

  @Test
  void stillAppliesCheapChecksBeforeReusingKnownFiles(@TempDir Path folder) throws Exception {
    Path filePath = folder.resolve("archive.zip");
    Files.writeString(filePath, "Not an image.");
    KnownFile knownFile =
        new KnownFile(
            "archive.zip",
            Files.size(filePath),
            Files.getLastModifiedTime(filePath).toInstant().truncatedTo(ChronoUnit.MICROS),
            "a".repeat(64));
    FileHasher hasher = spy(new FileHasher());

    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      ImageScanner scanner = new ImageScanner(hasher, workers);
      AllowedRoot allowedRoot = new ScanFolderValidator().validate(folder);
      ScanResult result = scanner.scan(allowedRoot, Map.of("archive.zip", knownFile));

      assertEquals(new ScanSummary(0, 0, 0, 1), result.summary());
      assertEquals(RejectionReason.UNSUPPORTED_EXTENSION, result.rejected().getFirst().reason());
      verify(hasher, never()).hash(any(Path.class));
    }
  }

  @Test
  void reportsZeroCountersForEmptyFolder(@TempDir Path folder) throws Exception {
    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      ImageScanner scanner = new ImageScanner(new FileHasher(), workers);
      AllowedRoot allowedRoot = new ScanFolderValidator().validate(folder);

      ScanResult result = scanner.scan(allowedRoot, Map.of());

      assertEquals(new ScanSummary(0, 0, 0, 0), result.summary());
      assertTrue(result.accepted().isEmpty());
      assertTrue(result.rejected().isEmpty());
    }
  }

  @Test
  void publishesCountersOnlyForCompletedEntriesOnTheCoordinatorThread(@TempDir Path folder)
      throws Exception {
    Path knownPath = folder.resolve("known.png");
    writeImage(knownPath, 2);
    writeImage(folder.resolve("new.png"), 3);
    Files.writeString(folder.resolve("notes.txt"), "Not an image.");
    KnownFile known =
        new KnownFile(
            "known.png",
            Files.size(knownPath),
            Files.getLastModifiedTime(knownPath).toInstant().truncatedTo(ChronoUnit.MICROS),
            "a".repeat(64));
    List<ScanSummary> snapshots = new ArrayList<>();
    List<Thread> callbackThreads = new ArrayList<>();
    Thread coordinator = Thread.currentThread();
    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      ImageScanner scanner = new ImageScanner(new FileHasher(), workers);
      ScanResult result =
          scanner.scan(
              new ScanFolderValidator().validate(folder),
              Map.of("known.png", known),
              summary -> {
                snapshots.add(summary);
                callbackThreads.add(Thread.currentThread());
              });

      assertEquals(3, snapshots.size());
      for (int index = 0; index < snapshots.size(); index++) {
        ScanSummary snapshot = snapshots.get(index);
        int processed =
            snapshot.newImages() + snapshot.rescannedImages()
                + snapshot.unchangedImages() + snapshot.rejectedFiles();
        assertEquals(index + 1, processed);
        assertEquals(coordinator, callbackThreads.get(index));
      }
      assertEquals(new ScanSummary(1, 0, 1, 1), snapshots.getLast());
      assertEquals(result.summary(), snapshots.getLast());
    }
  }

  @Test
  void emptyFolderDoesNotInventACompletedEntry(@TempDir Path folder) throws Exception {
    List<ScanSummary> snapshots = new ArrayList<>();
    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      ImageScanner scanner = new ImageScanner(new FileHasher(), workers);
      ScanResult result = scanner.scan(new AllowedRoot(folder), Map.of(), snapshots::add);
      assertTrue(snapshots.isEmpty());
      assertEquals(new ScanSummary(0, 0, 0, 0), result.summary());
    }
  }

  private static void writeImage(Path path, int width) throws Exception {
    assertTrue(
        ImageIO.write(
            new BufferedImage(width, width, BufferedImage.TYPE_INT_RGB), "png", path.toFile()));
  }

  private static KnownFile snapshot(AcceptedImage image) {
    Instant savedModificationTime = image.lastModifiedAt().truncatedTo(ChronoUnit.MICROS);
    return new KnownFile(
        image.originalName(), image.sizeBytes(), savedModificationTime, image.hash());
  }
}
