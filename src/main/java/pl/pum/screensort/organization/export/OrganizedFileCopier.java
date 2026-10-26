package pl.pum.screensort.organization.export;

import pl.pum.screensort.files.Sha256;
import pl.pum.screensort.files.SourcePath;
import pl.pum.screensort.organization.review.ImageCategory;
import pl.pum.screensort.files.FileNames;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import pl.pum.screensort.naming.FileNameRules;
import pl.pum.screensort.scanning.folder.ScanFolderValidator;

/** Streams one approved source into an owned output directory. Source files are read only. */
@Component
public class OrganizedFileCopier {
  public static class CopyProblem extends IOException {
    public CopyProblem(String message) { super(message); }
  }

  private final ScanFolderValidator folders;

  public OrganizedFileCopier(ScanFolderValidator folders) { this.folders = folders; }

  public String copy(ExportStore.Item item, Path outputFolder) throws IOException {
    if (FileNameRules.problem(item.approvedName(), item.originalName()).isPresent()) {
      throw new CopyProblem("Zatwierdzona nazwa wymaga poprawienia.");
    }
    Path storedRoot = Path.of(item.sourceFolder());
    Path root = folders.validate(storedRoot).path();
    var child = SourcePath.directChild(root, item.relativePath());
    if (!storedRoot.equals(root) || child.isEmpty()) {
      throw new CopyProblem("Lokalizacja źródła wymaga ponownego skanowania.");
    }
    Path source = child.get();
    if (!source.equals(source.toRealPath())) {
      throw new CopyProblem("Plik źródłowy jest przekierowaną ścieżką.");
    }
    var before = sourceAttributes(source, item.sizeBytes());
    requireDirectory(outputFolder);
    Path category = outputFolder.resolve(categoryFolder(item.category()));
    try { Files.createDirectory(category); } catch (FileAlreadyExistsException ignored) { /* Validate below. */ }
    requireDirectory(category);
    Path temporary = Files.createTempFile(category, ".screensort-", ".part");
    try {
      MessageDigest digest = Sha256.digest();
      long count = 0;
      byte[] buffer = new byte[64 * 1024];
      try (var channel = Files.newByteChannel(source, Set.of(READ, NOFOLLOW_LINKS));
          var input = Channels.newInputStream(channel);
          var output = Files.newOutputStream(temporary, WRITE, NOFOLLOW_LINKS)) {
        int read;
        while ((read = input.read(buffer)) != -1) {
          if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
          count += read;
          if (count > item.sizeBytes()) throw new CopyProblem("Plik zmienił się od skanowania. Zeskanuj go ponownie.");
          digest.update(buffer, 0, read);
          output.write(buffer, 0, read);
        }
      }
      var after = sourceAttributes(source, item.sizeBytes());
      if (count != item.sizeBytes() || !item.sha256().equals(Sha256.hex(digest))
          || !before.lastModifiedTime().equals(after.lastModifiedTime())
          || !Objects.equals(before.fileKey(), after.fileKey()) || !source.equals(source.toRealPath())
          || !root.equals(folders.validate(root).path())) {
        throw new CopyProblem("Plik zmienił się od zatwierdzenia. Zeskanuj i sprawdź go ponownie.");
      }
      requireDirectory(outputFolder);
      requireDirectory(category);
      for (int index = 1; index <= 10_000; index++) {
        Path target = category.resolve(collisionName(item.approvedName(), index));
        try {
          Files.move(temporary, target);
          return outputFolder.relativize(target).toString();
        } catch (FileAlreadyExistsException ignored) { /* Existing files keep their names and content. */ }
      }
      throw new CopyProblem("Zbyt wiele plików ma tę samą nazwę w folderze docelowym.");
    } finally {
      // The only removable file is the random temporary file created by this invocation.
      if (Files.isDirectory(category, NOFOLLOW_LINKS) && category.equals(category.toRealPath())) {
        Files.deleteIfExists(temporary);
      }
    }
  }

  public static String categoryFolder(String category) throws CopyProblem {
    return ImageCategory.parse(category).map(ImageCategory::folder)
        .orElseThrow(() -> new CopyProblem("Wybierz poprawną kategorię obrazu."));
  }

  private static String collisionName(String name, int index) {
    if (index == 1) return name;
    String extension = FileNames.extension(name);
    String suffix = " (" + index + ")" + extension;
    String base = name.substring(0, name.length() - extension.length());
    while ((base + suffix).getBytes(StandardCharsets.UTF_8).length > 255 || base.length() + suffix.length() > 255) {
      base = base.substring(0, base.offsetByCodePoints(base.length(), -1));
    }
    return base.stripTrailing() + suffix;
  }

  private static BasicFileAttributes sourceAttributes(Path source, long expectedBytes) throws IOException {
    var attrs = Files.readAttributes(source, BasicFileAttributes.class, NOFOLLOW_LINKS);
    if (!attrs.isRegularFile() || attrs.isSymbolicLink() || attrs.size() != expectedBytes) {
      throw new CopyProblem("Plik zmienił się lub nie jest dostępny jako zwykły plik. Zeskanuj folder ponownie.");
    }
    return attrs;
  }

  private static void requireDirectory(Path path) throws IOException {
    if (!Files.isDirectory(path, NOFOLLOW_LINKS) || !path.equals(path.toRealPath())) {
      throw new CopyProblem("Folder wynikowy został przeniesiony lub przekierowany.");
    }
  }
}
