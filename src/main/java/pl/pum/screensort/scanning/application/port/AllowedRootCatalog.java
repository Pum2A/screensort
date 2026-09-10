package pl.pum.screensort.scanning.application.port;

import pl.pum.screensort.scanning.domain.AllowedRoot;

public interface AllowedRootCatalog {

    AllowedRoot save(AllowedRoot allowedRoot);

}
