package pl.pum.screensort.scanning.image;

import static pl.pum.screensort.scanning.result.RejectionReason.METADATA_UNREADABLE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import pl.pum.screensort.scanning.hashing.FileHasher;
import pl.pum.screensort.scanning.folder.AllowedRoot;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;
import pl.pum.screensort.scanning.result.AcceptedImage;
import pl.pum.screensort.scanning.result.RejectedFile;
import pl.pum.screensort.scanning.result.RejectionReason;
import pl.pum.screensort.scanning.result.ScanResult;

@Service
public class ImageScanner {
  private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;
  private static final int MAX_IN_FLIGHT = 4;

  private final ScanFolderValidator folderValidator;
  private final FileHasher hasher;
  private final ExecutorService scanWorkers;

  public ImageScanner(
      ScanFolderValidator folderValidator,
      FileHasher hasher,
      @Qualifier("scanWorkers") ExecutorService scanWorkers) {
    this.folderValidator = folderValidator;
    this.hasher = hasher;
    this.scanWorkers = scanWorkers;
  }

  public ScanResult scan(Path folder) throws IOException {
    long scanStartedAt = System.nanoTime();
    AllowedRoot allowedRoot = folderValidator.validate(folder);
    List<AcceptedImage> accepted = new ArrayList<>();
    List<RejectedFile> rejected = new ArrayList<>();
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

          Future<ProcessedFile> task = completed.submit(() -> processFile(path));

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
          System.out.println("Accepted: " + image.path());
        } else if (outcome instanceof FileOutcome.Rejected rejectedOutcome) {
          RejectedFile file = rejectedOutcome.file();
          rejected.add(file);
          System.out.println("Rejected: " + file.reason());
        }
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

    return new ScanResult(allowedRoot, accepted, rejected);
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

  private AcceptedImage createAcceptedImage(Path path, String hash) throws IOException {
    String originalName = path.getFileName().toString();
    String extension = getExtension(path);
    long sizeBytes = Files.size(path);
    Instant lastModifiedAt = Files.getLastModifiedTime(path).toInstant();
    return new AcceptedImage(path, originalName, extension, sizeBytes, lastModifiedAt, hash);
  }

  private Optional<RejectionReason> findRejectionReason(Path path, ScanTiming timing) {
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
    long beforeImageRead = System.nanoTime();
    boolean readableImage = isReadableImage(path);
    timing.imageReadNanos += System.nanoTime() - beforeImageRead;
    timing.imageReadCount++;
    if (!readableImage) {
      return Optional.of(RejectionReason.UNREADABLE_IMAGE);
    }

    return Optional.empty();
  }

  private FileOutcome fileOutcome(Path path, ScanTiming timing) {

    Optional<RejectionReason> rejectionReason = findRejectionReason(path, timing);
    if (rejectionReason.isPresent()) {
      RejectedFile file =
          new RejectedFile(path, path.getFileName().toString(), rejectionReason.get());
      return new FileOutcome.Rejected(file);
    } else {
      long beforeHash = System.nanoTime();
      Optional<String> hash = hasher.hash(path);
      timing.hashNanos += System.nanoTime() - beforeHash;
      timing.hashCount++;
      if (hash.isEmpty()) {
        RejectedFile file =
            new RejectedFile(path, path.getFileName().toString(), METADATA_UNREADABLE);
        return new FileOutcome.Rejected(file);
      }
      try {
        AcceptedImage image = createAcceptedImage(path, hash.get());
        return new FileOutcome.Accepted(image);
      } catch (IOException exception) {
        RejectedFile file =
            new RejectedFile(path, path.getFileName().toString(), METADATA_UNREADABLE);
        return new FileOutcome.Rejected(file);
      }
    }
  }

  private ProcessedFile processFile(Path path) {
    ScanTiming timing = new ScanTiming();
    FileOutcome fileOutcome = fileOutcome(path, timing);
    return new ProcessedFile(fileOutcome, timing);
  }

  private sealed interface FileOutcome {
    record Accepted(AcceptedImage image) implements FileOutcome {}

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
