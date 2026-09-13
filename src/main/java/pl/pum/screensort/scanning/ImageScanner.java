package pl.pum.screensort.scanning;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


public class ImageScanner {

    public List<ScannedImage> scan(Path folder) throws IOException {
        List<ScannedImage> acceptedImages = new ArrayList<>();

        try(DirectoryStream<Path> files = Files.newDirectoryStream(folder)){
            for(Path path: files){
                if(isSupportedImage(path)){
                    ScannedImage image = createScannedImage(path);
                    acceptedImages.add(image);
                }
            }
        }


        return acceptedImages;


    }
    private boolean hasSupportedExtension(Path path){
        String fileName = path.getFileName().toString().toLowerCase();

        return fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg");

    }

    private boolean hasAllowedSize(Path path){
      try{
          long fileSizeBytes = Files.size(path);
          return fileSizeBytes <= 50L * 1024 * 1024;
      } catch(IOException exception){
          return false;
      }

    }

    private boolean isReadableImage(Path path) {
        try{
            return ImageIO.read(path.toFile()) != null;
        } catch (IOException exception){
            return false;
        }
    }

    // important check => we can only return FILES
    private boolean isSupportedImage(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && hasSupportedExtension(path)
                && hasAllowedSize(path)
                && isReadableImage(path);
    }
    private String getExtension(Path path) {
        String fileName = path.getFileName().toString();
        int dotPosition = fileName.lastIndexOf('.');

        return fileName.substring(dotPosition + 1).toLowerCase();
    }

    private ScannedImage createScannedImage(Path path) throws IOException{
        String originalName = path.getFileName().toString();
        String extension = getExtension(path);
        long sizeBytes = Files.size(path);
        Instant lastModifiedAt = Files.getLastModifiedTime(path).toInstant();
        return new ScannedImage(
                path,
                originalName,
                extension,
                sizeBytes,
                lastModifiedAt
        );
    }


}
