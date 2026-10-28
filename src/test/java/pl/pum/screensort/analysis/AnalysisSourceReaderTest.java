package pl.pum.screensort.analysis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.pum.screensort.gallery.preview.CatalogImageSourceReader;
import pl.pum.screensort.gallery.reading.ScanFolderValidator;

class AnalysisSourceReaderTest {
  @Test void readsActualBytesWithoutChangingTheSource(@TempDir Path folder) throws Exception {
    byte[] original = { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3 };
    Path file = folder.resolve("photo.png");
    Files.write(file, original);
    var before = Files.getLastModifiedTime(file);
    var catalog = mock(CatalogImageSourceReader.class);
    when(catalog.read(1)).thenReturn(new CatalogImageSourceReader.ImageSource(
        1, folder.toRealPath().toString(), "photo.png", "a".repeat(64)));

    var input = new AnalysisSourceReader(catalog, new ScanFolderValidator()).read(1);

    assertThat(input.bytes()).containsExactly(original);
    assertThat(input.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)));
    assertThat(input.mediaType()).isEqualTo("image/png");
    assertThat(Files.readAllBytes(file)).containsExactly(original);
    assertThat(Files.getLastModifiedTime(file)).isEqualTo(before);
  }

  @Test void rejectsEscapingRelativePath(@TempDir Path folder) throws Exception {
    var catalog = mock(CatalogImageSourceReader.class);
    when(catalog.read(1)).thenReturn(new CatalogImageSourceReader.ImageSource(
        1, folder.toRealPath().toString(), "../outside.png", "a".repeat(64)));
    assertThatThrownBy(() -> new AnalysisSourceReader(catalog, new ScanFolderValidator()).read(1))
        .isInstanceOf(AnalysisException.class);
  }
}
