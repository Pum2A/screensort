package pl.pum.screensort.scanning;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

@Component
public class ScanFolderValidator {

    private static final List<String> PROTECTED_PATH_ENVIRONMENT_VARIABLES = List.of(
            "SystemRoot",
            "ProgramFiles",
            "ProgramFiles(x86)",
            "ProgramData"
    );

    public AllowedRoot validate(Path requestedFolder) throws IOException {
        if (requestedFolder == null) {
            throw new InvalidScanFolderException("Nie podano folderu do skanowania.");
        }

        Path normalizedFolder = requestedFolder.toAbsolutePath().normalize();

        if (!Files.exists(normalizedFolder, LinkOption.NOFOLLOW_LINKS)) {
            throw new InvalidScanFolderException("Wybrany folder nie istnieje.");
        }

        if (Files.isSymbolicLink(normalizedFolder)) {
            throw new InvalidScanFolderException("Folder będący linkiem nie może zostać przeskanowany.");
        }

        Path realFolder = normalizedFolder.toRealPath();

        if (!Files.isDirectory(realFolder, LinkOption.NOFOLLOW_LINKS)) {
            throw new InvalidScanFolderException("Wybrana ścieżka nie jest folderem.");
        }

        if (!Files.isReadable(realFolder)) {
            throw new InvalidScanFolderException("Brak uprawnień do odczytu wybranego folderu.");
        }

        if (realFolder.equals(realFolder.getRoot())) {
            throw new InvalidScanFolderException("Nie można skanować całego dysku.");
        }

        if (isCurrentUserHome(realFolder)) {
            throw new InvalidScanFolderException("Nie można skanować całego katalogu użytkownika.");
        }

        if (isInsideProtectedSystemPath(realFolder)) {
            throw new InvalidScanFolderException("Nie można skanować chronionego katalogu systemowego.");
        }

        return new AllowedRoot(realFolder);
    }

    private boolean isCurrentUserHome(Path folder) throws IOException {
        String userHomeValue = System.getProperty("user.home");

        if (userHomeValue == null || userHomeValue.isBlank()) {
            return false;
        }

        Path userHome = Path.of(userHomeValue).toAbsolutePath().normalize();
        return Files.exists(userHome) && Files.isSameFile(folder, userHome);
    }

    private boolean isInsideProtectedSystemPath(Path folder) {
        for (String variableName : PROTECTED_PATH_ENVIRONMENT_VARIABLES) {
            String protectedPathValue = System.getenv(variableName);

            if (protectedPathValue == null || protectedPathValue.isBlank()) {
                continue;
            }

            try {
                Path protectedPath = Path.of(protectedPathValue).toAbsolutePath().normalize();

                if (folder.startsWith(protectedPath)) {
                    return true;
                }
            } catch (InvalidPathException ignored) {
                // An invalid optional environment value does not make a user folder invalid.
            }
        }

        return false;
    }
}
