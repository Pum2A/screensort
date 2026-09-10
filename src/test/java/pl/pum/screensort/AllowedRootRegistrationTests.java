package pl.pum.screensort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pl.pum.screensort.scanning.application.RegisterAllowedRoot;
import pl.pum.screensort.scanning.domain.AllowedRoot;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class AllowedRootRegistrationTests {

    @Autowired
    private RegisterAllowedRoot registerAllowedRoot;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    private Path temporaryDirectory;

    @BeforeEach
    void clearAllowedRoots() {
        this.jdbcTemplate.update("DELETE FROM allowed_root");
    }

    @Test
    void repeatedRegistrationUpdatesThePolicyWithoutCreatingADuplicate() throws IOException {
        AllowedRoot first = this.registerAllowedRoot.register(this.temporaryDirectory, false);
        AllowedRoot second = this.registerAllowedRoot.register(this.temporaryDirectory, true);

        Long storedRoots = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM allowed_root", Long.class);
        assertThat(first.canonicalPath()).isEqualTo(this.temporaryDirectory.toRealPath());
        assertThat(second.recursiveScan()).isTrue();
        assertThat(storedRoots).isEqualTo(1L);
    }

}
