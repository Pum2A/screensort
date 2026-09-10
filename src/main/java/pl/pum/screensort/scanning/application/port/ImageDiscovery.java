package pl.pum.screensort.scanning.application.port;

import pl.pum.screensort.scanning.domain.AllowedRoot;
import pl.pum.screensort.scanning.domain.DiscoveredImage;

import java.util.List;

/**
 * Application-owned contract for finding supported images in an allowed root.
 */
public interface ImageDiscovery {

    /**
     * Discovers image metadata without modifying any source file.
     */
    List<DiscoveredImage> discover(AllowedRoot allowedRoot);

}
