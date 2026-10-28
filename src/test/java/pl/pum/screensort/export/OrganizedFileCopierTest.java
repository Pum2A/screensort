package pl.pum.screensort.export;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.pum.screensort.gallery.reading.ScanFolderValidator;

class OrganizedFileCopierTest {
  private final OrganizedFileCopier copier = new OrganizedFileCopier(new ScanFolderValidator());

  @Test void copiesExactBytesAndKeepsTheSourceUntouched(@TempDir Path directory) throws Exception {
    Path source = Files.createDirectory(directory.resolve("source"));
    byte[] bytes = new byte[200_000];
    for (int index = 0; index < bytes.length; index++) bytes[index] = (byte) index;
    Path original = Files.write(source.resolve("IMG.png"), bytes);
    var modified = Files.getLastModifiedTime(original);
    Path output = Files.createDirectory(directory.resolve("output"));

    String target = copier.copy(item(source, bytes, "kubek.png"), output);

    assertThat(Files.readAllBytes(output.resolve(target))).isEqualTo(bytes);
    assertThat(Files.readAllBytes(original)).isEqualTo(bytes);
    assertThat(Files.getLastModifiedTime(original)).isEqualTo(modified);
    assertThat(target).isEqualTo(Path.of("Fotografie", "kubek.png").toString());
  }

  @Test void preservesExistingFilesAndGivesDuplicatesAnotherName(@TempDir Path directory) throws Exception {
    Path source = Files.createDirectory(directory.resolve("source"));
    byte[] bytes = {1, 2, 3};
    Files.write(source.resolve("IMG.png"), bytes);
    Path output = Files.createDirectory(directory.resolve("output"));
    Path category = Files.createDirectory(output.resolve("Fotografie"));
    Files.write(category.resolve("kubek.png"), new byte[]{9});

    String target = copier.copy(item(source, bytes, "kubek.png"), output);

    assertThat(target).isEqualTo(Path.of("Fotografie", "kubek (2).png").toString());
    assertThat(Files.readAllBytes(category.resolve("kubek.png"))).containsExactly((byte) 9);
    assertThat(Files.readAllBytes(output.resolve(target))).isEqualTo(bytes);
  }

  @Test void rejectsChangedContentEvenWhenTheSizeIsTheSame(@TempDir Path directory) throws Exception {
    Path source = Files.createDirectory(directory.resolve("source"));
    byte[] approved = {1, 2, 3};
    Files.write(source.resolve("IMG.png"), new byte[]{4, 5, 6});
    Path output = Files.createDirectory(directory.resolve("output"));

    assertThatThrownBy(() -> copier.copy(item(source, approved, "kubek.png"), output))
        .isInstanceOf(OrganizedFileCopier.CopyProblem.class);
    try (var entries = Files.list(output.resolve("Fotografie"))) {
      assertThat(entries.toList()).isEmpty();
    }
  }

  @Test void rejectsTraversalAndKeepsTheOutputEmpty(@TempDir Path directory) throws Exception {
    Path source = Files.createDirectory(directory.resolve("source"));
    byte[] bytes = {1, 2, 3};
    Files.write(source.resolve("IMG.png"), bytes);
    Path output = Files.createDirectory(directory.resolve("output"));
    assertThatThrownBy(() -> copier.copy(item(source, bytes, "../outside.png"), output))
        .isInstanceOf(OrganizedFileCopier.CopyProblem.class);
    try (var entries = Files.list(output)) {assertThat(entries.toList()).isEmpty();}
  }

  @Test void longUnicodeNamesStayValidWhenAConflictNeedsASuffix(@TempDir Path directory) throws Exception {
    Path source = Files.createDirectory(directory.resolve("source"));
    byte[] bytes = {1, 2, 3};
    Files.write(source.resolve("IMG.png"), bytes);
    Path output = Files.createDirectory(directory.resolve("output"));
    var item = item(source, bytes, "ą".repeat(125) + ".png");
    String first = copier.copy(item, output);
    String second = copier.copy(item, output);
    assertThat(first).isNotEqualTo(second);
    assertThat(pl.pum.screensort.naming.FileNameRules.problem(Path.of(second).getFileName().toString(), "IMG.png")).isEmpty();
    assertThat(Files.readAllBytes(output.resolve(second))).isEqualTo(bytes);
  }

  private ExportStore.Item item(Path source, byte[] bytes, String approvedName) throws Exception {
    return new ExportStore.Item(1, source.toRealPath().toString(), "IMG.png", "IMG.png", bytes.length,
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), approvedName, "PHOTOGRAPHS", 0, 0);
  }
}
