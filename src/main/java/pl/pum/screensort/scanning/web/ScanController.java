package pl.pum.screensort.scanning.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.pum.screensort.scanning.image.ImageScanner;
import pl.pum.screensort.scanning.image.ScannedImage;
import pl.pum.screensort.scanning.safety.InvalidScanFolderException;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

@Controller
public class ScanController {

    private final ImageScanner imageScanner;

    public ScanController(ImageScanner imageScanner) {
        this.imageScanner = imageScanner;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("folderPath", "demo-images");
        return "scanning/scan";
    }

    @PostMapping("/scan")
    public String scan(@RequestParam String folderPath, Model model) {
        model.addAttribute("folderPath", folderPath);

        if (folderPath.isBlank()) {
            model.addAttribute("errorMessage", "Podaj ścieżkę do folderu.");
            return "scanning/scan";
        }

        try {
            List<ScannedImage> images = imageScanner.scan(Path.of(folderPath.trim()));


            model.addAttribute("images", images);
        } catch (InvalidPathException exception) {
            model.addAttribute("errorMessage", "Ścieżka ma niepoprawny format.");
        } catch (InvalidScanFolderException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
        } catch (IOException exception) {
            model.addAttribute(
                    "errorMessage",
                    "Nie udało się otworzyć folderu. Sprawdź, czy istnieje i czy masz do niego dostęp."
            );
        }

        return "scanning/scan";
    }
}
