package pl.pum.screensort;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


public class ImageScanner {

//    public List<Path> scan(Path folder){
//
//
//
//    }
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

    private boolean isSupportedImage(Path path) {
        return hasSupportedExtension(path)
                && hasAllowedSize(path)
                && isReadableImage(path);
    }



}
