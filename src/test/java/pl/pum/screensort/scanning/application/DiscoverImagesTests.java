package pl.pum.screensort.scanning.application;

import org.junit.jupiter.api.Test;
import pl.pum.screensort.scanning.application.port.ImageDiscovery;
import pl.pum.screensort.scanning.domain.AllowedRoot;
import pl.pum.screensort.scanning.domain.DiscoveredImage;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoverImagesTests {

    @Test
    void passesTheAllowedRootToTheDiscoveryPort() {
        Path absolutePath = Path.of(System.getProperty("java.io.tmpdir"))
                .toAbsolutePath()
                .resolve("screenshots");
        AllowedRoot allowedRoot = new AllowedRoot(absolutePath, false);
        AtomicReference<AllowedRoot> receivedRoot = new AtomicReference<>();
        ImageDiscovery fakeDiscovery = root -> {
            receivedRoot.set(root);
            return List.of();
        };

        List<DiscoveredImage> result = new DiscoverImages(fakeDiscovery).discover(allowedRoot);

        assertThat(receivedRoot).hasValue(allowedRoot);
        assertThat(result).isEmpty();
    }

}
