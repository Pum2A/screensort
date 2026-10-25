package pl.pum.screensort.catalog.preview;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import org.w3c.dom.Node;

/**
 * Phones store photos sideways and record how to turn them in the EXIF "Orientation" tag.
 * ImageIO ignores that tag, so without this a portrait photo shows lying on its side.
 * Values 1-8 follow the EXIF/TIFF specification; anything unreadable counts as 1 (as stored).
 */
final class ExifOrientation {
  static final int NORMAL = 1;
  private static final int APP1 = 0xE1;
  private static final int ORIENTATION_TAG = 0x0112;
  private static final int SHORT_TYPE = 3;
  private static final byte[] EXIF_HEADER = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);

  private ExifOrientation() {}

  /** Reads the tag from JPEG metadata (javax_imageio_jpeg_image_1.0), where APP1 is an "unknown" marker. */
  static int read(IIOMetadata metadata) {
    if (metadata == null) return NORMAL;
    Node root = metadata.getAsTree("javax_imageio_jpeg_image_1.0");
    for (Node section = root.getFirstChild(); section != null; section = section.getNextSibling()) {
      if (!"markerSequence".equals(section.getNodeName())) continue;
      for (Node marker = section.getFirstChild(); marker != null; marker = marker.getNextSibling()) {
        if (marker instanceof IIOMetadataNode node && "unknown".equals(node.getNodeName())
            && String.valueOf(APP1).equals(node.getAttribute("MarkerTag"))
            && node.getUserObject() instanceof byte[] data) {
          int orientation = parse(data);
          if (orientation != NORMAL) return orientation;
        }
      }
    }
    return NORMAL;
  }

  /** Parses an APP1 payload: "Exif\0\0", then a TIFF header and the first image directory. */
  static int parse(byte[] app1) {
    try {
      if (app1.length < EXIF_HEADER.length + 8) return NORMAL;
      for (int i = 0; i < EXIF_HEADER.length; i++) {
        if (app1[i] != EXIF_HEADER[i]) return NORMAL;
      }
      ByteBuffer tiff = ByteBuffer.wrap(app1, EXIF_HEADER.length, app1.length - EXIF_HEADER.length).slice();
      if (tiff.get(0) == 'I' && tiff.get(1) == 'I') tiff.order(ByteOrder.LITTLE_ENDIAN);
      else if (tiff.get(0) == 'M' && tiff.get(1) == 'M') tiff.order(ByteOrder.BIG_ENDIAN);
      else return NORMAL;
      if (tiff.getShort(2) != 42) return NORMAL;
      long directory = Integer.toUnsignedLong(tiff.getInt(4));
      if (directory < 8 || directory + 2 > tiff.limit()) return NORMAL;
      int entries = Short.toUnsignedInt(tiff.getShort((int) directory));
      for (int i = 0; i < entries; i++) {
        long entry = directory + 2 + 12L * i;
        if (entry + 12 > tiff.limit()) return NORMAL;
        int at = (int) entry;
        if (Short.toUnsignedInt(tiff.getShort(at)) != ORIENTATION_TAG) continue;
        if (Short.toUnsignedInt(tiff.getShort(at + 2)) != SHORT_TYPE || tiff.getInt(at + 4) != 1) return NORMAL;
        int value = Short.toUnsignedInt(tiff.getShort(at + 8));
        return value >= 1 && value <= 8 ? value : NORMAL;
      }
      return NORMAL;
    } catch (IndexOutOfBoundsException e) {
      return NORMAL;
    }
  }

  /** Returns the image as it should be seen. Orientations 5-8 swap width and height. */
  static BufferedImage apply(BufferedImage source, int orientation) {
    if (orientation < 2 || orientation > 8) return source;
    int width = source.getWidth();
    int height = source.getHeight();
    boolean swapped = orientation >= 5;
    int outWidth = swapped ? height : width;
    int outHeight = swapped ? width : height;
    int[] in = source.getRGB(0, 0, width, height, null, 0, width);
    int[] out = new int[in.length];
    for (int y = 0; y < outHeight; y++) {
      for (int x = 0; x < outWidth; x++) {
        // For each visible pixel, which stored pixel belongs there.
        int sx, sy;
        switch (orientation) {
          case 2 -> { sx = width - 1 - x; sy = y; }               // mirrored
          case 3 -> { sx = width - 1 - x; sy = height - 1 - y; }  // upside down
          case 4 -> { sx = x; sy = height - 1 - y; }              // mirrored upside down
          case 5 -> { sx = y; sy = x; }                           // mirrored, turned left
          case 6 -> { sx = y; sy = height - 1 - x; }              // turn right (clockwise)
          case 7 -> { sx = width - 1 - y; sy = height - 1 - x; }  // mirrored, turned right
          default -> { sx = width - 1 - y; sy = x; }              // 8: turn left
        }
        out[y * outWidth + x] = in[sy * width + sx];
      }
    }
    var result = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB);
    result.setRGB(0, 0, outWidth, outHeight, out, 0, outWidth);
    return result;
  }
}
