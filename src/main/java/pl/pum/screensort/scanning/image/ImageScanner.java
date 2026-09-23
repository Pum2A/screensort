package pl.pum.screensort.scanning.image;

import static pl.pum.screensort.scanning.image.RejectionReason.METADATA_UNREADABLE;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    AllowedRoot allowedRoot = folderValidator.validate(folder);
    List<AcceptedImage> accepted = new ArrayList<>();
    List<RejectedFile> rejected = new ArrayList<>();

    try (DirectoryStream<Path> files = Files.newDirectoryStream(allowedRoot.path())) {
      for (Path path : files) {
        Optional<RejectionReason> reason = classify(path);
        if (reason.isEmpty()) {
          Optional<String> hash = hasher.hash(path);
          if (hash.isPresent()) {
            accepted.add(createAcceptedImage(path, hash.get()));
            System.out.println("Accepted: " + path + " hash: " + hash.get());
          } else {
            rejected.add(
                new RejectedFile(path, path.getFileName().toString(), METADATA_UNREADABLE));
            System.out.println("Rejected file: " + path);
          }

        } else {
          rejected.add(new RejectedFile(path, path.getFileName().toString(), reason.get()));
          System.out.println("Rejected: " + reason.get());
        }
      }
    }

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

  private Optional<RejectionReason> classify(Path path) {
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
    if (!isReadableImage(path)) {
      return Optional.of(RejectionReason.UNREADABLE_IMAGE);
    }

    return Optional.empty();
  }
}
