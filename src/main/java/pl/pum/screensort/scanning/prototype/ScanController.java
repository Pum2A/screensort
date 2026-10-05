package pl.pum.screensort.scanning.prototype;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.pum.screensort.scanning.workflow.ScanWorkflow;
import pl.pum.screensort.scanning.result.ScanResult;
import pl.pum.screensort.scanning.folder.InvalidScanFolderException;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

@Controller
public class ScanController {

    private final ScanWorkflow scanWorkflow;

    public ScanController(ScanWorkflow scanWorkflow) {
        this.scanWorkflow = scanWorkflow;
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
            ScanResult result = scanWorkflow.scanAndSave(Path.of(folderPath.trim()));


            model.addAttribute("images", result.accepted());
            model.addAttribute("rejected", result.rejected());
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
