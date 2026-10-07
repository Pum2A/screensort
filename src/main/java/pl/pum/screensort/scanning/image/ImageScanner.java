package pl.pum.screensort.scanning.image;

import static pl.pum.screensort.scanning.result.RejectionReason.METADATA_UNREADABLE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import pl.pum.screensort.scanning.folder.AllowedRoot;
import pl.pum.screensort.scanning.hashing.FileHasher;
import pl.pum.screensort.scanning.knownfiles.KnownFile;
import pl.pum.screensort.scanning.result.AcceptedImage;
import pl.pum.screensort.scanning.result.RejectedFile;
import pl.pum.screensort.scanning.result.RejectionReason;
import pl.pum.screensort.scanning.result.ScanResult;
import pl.pum.screensort.scanning.result.ScanSummary;

@Service
public class ImageScanner {
  private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;
  private static final int MAX_IN_FLIGHT = 4;

  private final FileHasher hasher;
  private final ExecutorService scanWorkers;

  public ImageScanner(FileHasher hasher, @Qualifier("scanWorkers") ExecutorService scanWorkers) {
    this.hasher = hasher;
    this.scanWorkers = scanWorkers;
  }

  public ScanResult scan(AllowedRoot allowedRoot, Map<String, KnownFile> knownFilesByPath)
      throws IOException {
    return scan(allowedRoot, knownFilesByPath, summary -> {});
  }

  public ScanResult scan(
      AllowedRoot allowedRoot,
      Map<String, KnownFile> knownFilesByPath,
      Consumer<ScanSummary> onProgress)
      throws IOException {
    long scanStartedAt = System.nanoTime();
    List<AcceptedImage> accepted = new ArrayList<>();
    List<RejectedFile> rejected = new ArrayList<>();
    int newImages = 0;
    int rescannedImages = 0;
    int unchangedImages = 0;
    ScanTiming timing = new ScanTiming();
    ExecutorCompletionService<ProcessedFile> completed =
        new ExecutorCompletionService<>(scanWorkers);
    int inFlight = 0;
    List<Future<ProcessedFile>> pending = new ArrayList<>(MAX_IN_FLIGHT);

    try (Stream<Path> paths = Files.list(allowedRoot.path())) {
      Iterator<Path> entries = paths.iterator();

      while (entries.hasNext() || inFlight > 0) {

        // Zlecamy zadania, dopóki mamy wolne miejsca.
        while (inFlight < MAX_IN_FLIGHT && entries.hasNext()) {
          Path path = entries.next();

          Future<ProcessedFile> task =
              completed.submit(() -> processFile(path, allowedRoot, knownFilesByPath));

          pending.add(task);
          inFlight++;
        }

        // Odbieramy następne ukończone zadanie.
        Future<ProcessedFile> finished = completed.take();
        ProcessedFile processedFile = finished.get();

        pending.remove(finished);
        inFlight--;

        // Dotychczasowe zbieranie wyników i pomiarów.
        FileOutcome outcome = processedFile.fileOutcome();
        ScanTiming fileTiming = processedFile.timing();

        timing.imageReadNanos += fileTiming.imageReadNanos;
        timing.imageReadCount += fileTiming.imageReadCount;
        timing.hashNanos += fileTiming.hashNanos;
        timing.hashCount += fileTiming.hashCount;

        if (outcome instanceof FileOutcome.Accepted acceptedOutcome) {
          AcceptedImage image = acceptedOutcome.image();
          accepted.add(image);
          switch (acceptedOutcome.scanType()) {
            case NEW -> newImages++;
            case RESCANNED -> rescannedImages++;
            case UNCHANGED -> unchangedImages++;
          }
          System.out.println("Accepted: " + image.path());
        } else if (outcome instanceof FileOutcome.Rejected rejectedOutcome) {
          RejectedFile file = rejectedOutcome.file();
          rejected.add(file);
          System.out.println("Rejected: " + file.reason());
        }
        // Tylko wątek odbierający gotowe wyniki publikuje postęp, nie workery plików.
        onProgress.accept(
            new ScanSummary(newImages, rescannedImages, unchangedImages, rejected.size()));
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("Skan został przerwany.", exception);
    } catch (ExecutionException exception) {
      throw new IOException("Przetwarzanie pliku nie powiodło się.", exception.getCause());
    } finally {
      for (Future<ProcessedFile> task : pending) {
        task.cancel(true);
      }
    }

    long scanNanos = System.nanoTime() - scanStartedAt;
    System.out.printf(
        Locale.ROOT,
        "Scan timing: total=%.3f ms; imageReadSum=%.3f ms (%d calls); hashSum=%.3f ms (%d calls)%n",
        scanNanos / 1_000_000.0,
        timing.imageReadNanos / 1_000_000.0,
        timing.imageReadCount,
        timing.hashNanos / 1_000_000.0,
        timing.hashCount);

    ScanSummary summary =
        new ScanSummary(newImages, rescannedImages, unchangedImages, rejected.size());
    System.out.printf(
        "Scan summary: new=%d; rescanned=%d; unchanged=%d; rejected=%d%n",
        summary.newImages(),
        summary.rescannedImages(),
        summary.unchangedImages(),
        summary.rejectedFiles());
    return new ScanResult(allowedRoot, accepted, rejected, summary);
  }

  private boolean hasSupportedExtension(Path path) {
    String fileName = path.getFileName().toString().toLowerCase();

    return fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg");
  }

  private boolean hasAllowedSize(Path path) {
    try {
      long fileSizeBytes = Files.size(path);
      return fileSizeBytes <= MAX_FILE_SIZE_BYTES;
    } catch (IOException exception) {
      return false;
    }
  }

  private boolean isReadableImage(Path path) {
    try {
      return ImageIO.read(path.toFile()) != null;
    } catch (IOException exception) {
      return false;
    }
  }

  private String getExtension(Path path) {
    String fileName = path.getFileName().toString();
    int dotPosition = fileName.lastIndexOf('.');

    return fileName.substring(dotPosition + 1).toLowerCase();
  }

  private AcceptedImage createAcceptedImage(
      Path path, String hash, long sizeBytes, Instant lastModifiedAt) {
    String originalName = path.getFileName().toString();
    String extension = getExtension(path);
    return new AcceptedImage(path, originalName, extension, sizeBytes, lastModifiedAt, hash);
  }

  private Optional<RejectionReason> findBasicRejectionReason(Path path) {
    if (Files.isSymbolicLink(path)) {
      return Optional.of(RejectionReason.SYMBOLIC_LINK);
    }
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      return Optional.of(RejectionReason.NOT_A_REGULAR_FILE);
    }
    if (!hasSupportedExtension(path)) {
      return Optional.of(RejectionReason.UNSUPPORTED_EXTENSION);
    }
    if (!hasAllowedSize(path)) {
      return Optional.of(RejectionReason.TOO_LARGE);
    }

    return Optional.empty();
  }

  private FileOutcome fileOutcome(Path path, ScanTiming timing, KnownFile knownFile) {

    Optional<RejectionReason> rejectionReason = findBasicRejectionReason(path);
    if (rejectionReason.isPresent()) {
      RejectedFile file =
          new RejectedFile(path, path.getFileName().toString(), rejectionReason.get());
      return new FileOutcome.Rejected(file);
    } else {
      try {
        long sizeBytes = Files.size(path);
        Instant lastModifiedAt = Files.getLastModifiedTime(path).toInstant();
        if (knownFile != null
            && knownFile.sizeBytes() == sizeBytes
            && knownFile.lastModifiedAt().equals(lastModifiedAt.truncatedTo(ChronoUnit.MICROS))) {

          AcceptedImage image =
              createAcceptedImage(path, knownFile.sha256(), sizeBytes, lastModifiedAt);

          // Zgodność metadanych pozwala użyć poprzedniego hasha, bez odczytu zawartości.
          return new FileOutcome.Accepted(image, ImageScanType.UNCHANGED);
        }
        long beforeImageRead = System.nanoTime();
        boolean readableImage = isReadableImage(path);
        timing.imageReadNanos += System.nanoTime() - beforeImageRead;
        timing.imageReadCount++;
        if (!readableImage) {
          RejectedFile file =
              new RejectedFile(
                  path, path.getFileName().toString(), RejectionReason.UNREADABLE_IMAGE);
          return new FileOutcome.Rejected(file);
        }

        long beforeHash = System.nanoTime();
        Optional<String> hash = hasher.hash(path);
        timing.hashNanos += System.nanoTime() - beforeHash;
        timing.hashCount++;
        if (hash.isEmpty()) {
          RejectedFile file =
              new RejectedFile(path, path.getFileName().toString(), METADATA_UNREADABLE);
          return new FileOutcome.Rejected(file);
        }
        AcceptedImage image = createAcceptedImage(path, hash.get(), sizeBytes, lastModifiedAt);
        ImageScanType scanType = knownFile == null ? ImageScanType.NEW : ImageScanType.RESCANNED;
        return new FileOutcome.Accepted(image, scanType);
      } catch (IOException exception) {
        RejectedFile file =
            new RejectedFile(path, path.getFileName().toString(), METADATA_UNREADABLE);
        return new FileOutcome.Rejected(file);
      }
    }
  }

  private ProcessedFile processFile(
      Path path, AllowedRoot allowedRoot, Map<String, KnownFile> knownFilesByPath) {
    ScanTiming timing = new ScanTiming();
    String relativePath = allowedRoot.path().relativize(path).toString();
    KnownFile knownFile = knownFilesByPath.get(relativePath);
    FileOutcome fileOutcome = fileOutcome(path, timing, knownFile);
    return new ProcessedFile(fileOutcome, timing);
  }

  private enum ImageScanType {
    NEW,
    RESCANNED,
    UNCHANGED
  }

  private sealed interface FileOutcome {
    record Accepted(AcceptedImage image, ImageScanType scanType) implements FileOutcome {}

    record Rejected(RejectedFile file) implements FileOutcome {}
  }

  private record ProcessedFile(FileOutcome fileOutcome, ScanTiming timing) {}

  private static final class ScanTiming {
    private long imageReadNanos;
    private int imageReadCount;
    private long hashNanos;
    private int hashCount;
  }
}
