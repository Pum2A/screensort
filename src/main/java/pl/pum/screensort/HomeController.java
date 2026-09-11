package pl.pum.screensort;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Controller
public class HomeController {

    private final ImageScanner imageScanner = new ImageScanner();

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("folderPath", "demo-images");
        return "home";
    }

    @PostMapping("/scan")
    public String scan(@RequestParam String folderPath, Model model) {
        model.addAttribute("folderPath", folderPath);

        if (folderPath.isBlank()) {
            model.addAttribute("errorMessage", "Podaj ścieżkę do folderu.");
            return "home";
        }

        try {
            List<Path> imagePaths = imageScanner.scan(Path.of(folderPath.trim()));
            List<String> imageNames = new ArrayList<>();

            for (Path imagePath : imagePaths) {
                imageNames.add(imagePath.getFileName().toString());
            }

            model.addAttribute("images", imageNames);
        } catch (InvalidPathException exception) {
            model.addAttribute("errorMessage", "Ścieżka ma niepoprawny format.");
        } catch (IOException exception) {
            model.addAttribute(
                    "errorMessage",
                    "Nie udało się otworzyć folderu. Sprawdź, czy istnieje i czy masz do niego dostęp."
            );
        }

        return "home";
    }
}
