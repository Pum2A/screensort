package pl.pum.screensort;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public class ImageScanner {

    public List<Path> scan(Path folder) throws IOException {
        List<Path> acceptedImages = new ArrayList<>();

        try(DirectoryStream<Path> files = Files.newDirectoryStream(folder)){
            for(Path path: files){
                if(isSupportedImage(path)){
                    acceptedImages.add(path);
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



}
