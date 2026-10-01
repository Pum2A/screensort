package pl.pum.screensort.scanning.image;

import static pl.pum.screensort.scanning.image.RejectionReason.METADATA_UNREADABLE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import pl.pum.screensort.scanning.hashing.FileHasher;
import pl.pum.screensort.scanning.safety.AllowedRoot;
import pl.pum.screensort.scanning.safety.ScanFolderValidator;

@Service
public class ImageScanner {
  private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;

  private final ScanFolderValidator folderValidator;
  private final FileHasher hasher;

  public ImageScanner(ScanFolderValidator folderValidator, FileHasher hasher) {
    this.folderValidator = folderValidator;
    this.hasher = hasher;
  }

  public ScanResult scan(Path folder) throws IOException {
    long scanStartedAt = System.nanoTime();
    AllowedRoot allowedRoot = folderValidator.validate(folder);
    List<AcceptedImage> accepted = new ArrayList<>();
    List<RejectedFile> rejected = new ArrayList<>();
    ScanTiming timing = new ScanTiming();
    try (Stream<Path> paths = Files.list(allowedRoot.path())) {
      List<FileOutcome> outcomes = paths
          .map(path -> fileOutcome(path, timing))
          .toList();

      for (FileOutcome outcome : outcomes) {
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
    }

    long scanNanos = System.nanoTime() - scanStartedAt;
    long otherNanos = scanNanos - timing.imageReadNanos - timing.hashNanos;
    System.out.printf(Locale.ROOT,
        "Scan timing: total=%.3f ms; imageRead=%.3f ms (%d calls); hash=%.3f ms (%d calls); other=%.3f ms%n",
        scanNanos / 1_000_000.0,
        timing.imageReadNanos / 1_000_000.0, timing.imageReadCount,
        timing.hashNanos / 1_000_000.0, timing.hashCount,
        otherNanos / 1_000_000.0);

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

  private sealed interface FileOutcome {
    record Accepted(AcceptedImage image) implements FileOutcome {}

    record Rejected(RejectedFile file) implements FileOutcome {}
  }

  private static final class ScanTiming {
    private long imageReadNanos;
    private int imageReadCount;
    private long hashNanos;
    private int hashCount;
  }
}
