package pl.pum.screensort.scanning.application;

import org.springframework.stereotype.Service;
import pl.pum.screensort.scanning.application.port.ImageDiscovery;
import pl.pum.screensort.scanning.domain.AllowedRoot;
import pl.pum.screensort.scanning.domain.DiscoveredImage;

import java.util.List;
import java.util.Objects;

/**
 * Use case that exposes image discovery without knowing filesystem details.
 */
@Service
public class DiscoverImages {

    private final ImageDiscovery imageDiscovery;

    public DiscoverImages(ImageDiscovery imageDiscovery) {
        this.imageDiscovery = imageDiscovery;
    }

    public List<DiscoveredImage> discover(AllowedRoot allowedRoot) {
        return this.imageDiscovery.discover(Objects.requireNonNull(allowedRoot, "allowedRoot must not be null"));
    }

}
