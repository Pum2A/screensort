package pl.pum.screensort.scanning.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pum.screensort.scanning.application.port.AllowedRootCatalog;
import pl.pum.screensort.scanning.application.port.SourcePathResolver;
import pl.pum.screensort.scanning.domain.AllowedRoot;

import java.nio.file.Path;

@Service
public class RegisterAllowedRoot {

    private final SourcePathResolver sourcePathResolver;
    private final AllowedRootCatalog allowedRootCatalog;

    public RegisterAllowedRoot(SourcePathResolver sourcePathResolver, AllowedRootCatalog allowedRootCatalog) {
        this.sourcePathResolver = sourcePathResolver;
        this.allowedRootCatalog = allowedRootCatalog;
    }

    @Transactional
    public AllowedRoot register(Path requestedRoot, boolean recursiveScan) {
        Path canonicalPath = this.sourcePathResolver.resolveAllowedRoot(requestedRoot);
        return this.allowedRootCatalog.save(new AllowedRoot(canonicalPath, recursiveScan));
    }

}
