package pl.pum.screensort.gallery.preview;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExifOrientationTest {
  @Test void readsLittleEndianTiff() {
    byte[] app1 = app1(new byte[] {
        'I', 'I', 42, 0, 8, 0, 0, 0,
        1, 0,
        0x12, 0x01, 3, 0, 1, 0, 0, 0, 6, 0, 0, 0,
        0, 0, 0, 0});
    assertThat(ExifOrientation.parse(app1)).isEqualTo(6);
  }

  @Test void readsBigEndianTiff() {
    byte[] app1 = app1(new byte[] {
        'M', 'M', 0, 42, 0, 0, 0, 8,
        0, 1,
        0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 8, 0, 0,
        0, 0, 0, 0});
    assertThat(ExifOrientation.parse(app1)).isEqualTo(8);
  }

  @Test void brokenOrForeignBlocksMeanAsStored() {
    assertThat(ExifOrientation.parse(new byte[0])).isEqualTo(1);
    assertThat(ExifOrientation.parse("http://ns.adobe.com/xap/1.0/\0<x:xmpmeta/>".getBytes())).isEqualTo(1);
    // Directory offset points far past the end of the block.
    assertThat(ExifOrientation.parse(app1(new byte[] {'M', 'M', 0, 42, 0x7f, 0, 0, 0}))).isEqualTo(1);
    // Entry count claims more entries than the block holds.
    assertThat(ExifOrientation.parse(app1(new byte[] {'M', 'M', 0, 42, 0, 0, 0, 8, 0x7f, 0x7f}))).isEqualTo(1);
    // Out-of-range value.
    assertThat(ExifOrientation.parse(app1(new byte[] {
        'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1,
        0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 9, 0, 0}))).isEqualTo(1);
  }

  // Stored 3x2 image with pixels numbered row by row:  1 2 3 / 4 5 6.
  // Each row is the visible result, read row by row, for one orientation value.
  @ParameterizedTest
  @CsvSource({
      "1, 3, 2, 1 2 3 4 5 6",
      "2, 3, 2, 3 2 1 6 5 4",
      "3, 3, 2, 6 5 4 3 2 1",
      "4, 3, 2, 4 5 6 1 2 3",
      "5, 2, 3, 1 4 2 5 3 6",
      "6, 2, 3, 4 1 5 2 6 3",
      "7, 2, 3, 6 3 5 2 4 1",
      "8, 2, 3, 3 6 2 5 1 4"})
  void turnsPixelsAsTheSpecificationSays(int orientation, int width, int height, String expected) {
    var stored = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
    for (int i = 0; i < 6; i++) stored.setRGB(i % 3, i / 3, 0xff000000 | (i + 1));

    BufferedImage visible = ExifOrientation.apply(stored, orientation);

    assertThat(visible.getWidth()).isEqualTo(width);
    assertThat(visible.getHeight()).isEqualTo(height);
    var actual = new StringBuilder();
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        if (!actual.isEmpty()) actual.append(' ');
        actual.append(visible.getRGB(x, y) & 0xff);
      }
    }
    assertThat(actual.toString()).isEqualTo(expected);
  }

  private static byte[] app1(byte[] tiff) {
    byte[] result = new byte[6 + tiff.length];
    System.arraycopy("Exif\0\0".getBytes(), 0, result, 0, 6);
    System.arraycopy(tiff, 0, result, 6, tiff.length);
    return result;
  }
}
