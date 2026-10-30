package pl.pum.screensort.analysis;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AutoAnalysisController.class)
class AutoAnalysisJpaTest {
  static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    postgres.start();
    properties.add("spring.datasource.url", postgres::getJdbcUrl);
    properties.add("spring.datasource.username", postgres::getUsername);
    properties.add("spring.datasource.password", postgres::getPassword);
  }

  @AfterAll
  static void stopContainer() {
    if (postgres.isRunning()) postgres.stop();
  }

  @Autowired AutoAnalysisController settings;

  @Test void isOffUntilTheOwnerTurnsItOnAndRemembersTheChoice() {
    assertThat(settings.read().enabled()).isFalse();
    assertThat(settings.change(new AutoAnalysisController.AutoAnalysis(true)).enabled()).isTrue();
    assertThat(settings.read().enabled()).isTrue();
    settings.change(new AutoAnalysisController.AutoAnalysis(false));
    assertThat(settings.read().enabled()).isFalse();
  }

  @Test void aMissingValueIsRejected() {
    assertThatThrownBy(() -> settings.change(new AutoAnalysisController.AutoAnalysis(null)))
        .isInstanceOfSatisfying(AnalysisException.class, error -> assertThat(error.status()).isEqualTo(400));
  }
}
