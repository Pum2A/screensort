package pl.pum.screensort.analysis;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.pum.screensort.analysis.client.PythonAnalysisClient;
import tools.jackson.databind.json.JsonMapper;

class PythonAnalysisClientTest {
  @Test void catalogCanStartWithoutAnAnalysisToken() {
    var client = new PythonAnalysisClient(RestClient.builder(), JsonMapper.builder().build(), "http://127.0.0.1:8001", "");
    assertThatThrownBy(() -> client.analyze(new byte[]{1}, "image/png", true, true))
        .isInstanceOfSatisfying(AnalysisException.class, error -> assertThat(error.status()).isEqualTo(503));
  }
  @Test void nativeConnectionNeverAcceptsRemoteUrlsOrWeakCredentials() {
    var client = new PythonAnalysisClient(RestClient.builder(), JsonMapper.builder().build(), "http://127.0.0.1:8001", "");
    for (String url : new String[]{"https://evil.test:8001", "http://localhost:8001", "http://127.0.0.1:8001/secret",
        "http://127.0.0.1:8001?token=bad", "http://127.0.0.1:8001#fragment", "http://127.0.0.1"}) {
      assertThatThrownBy(() -> client.connect(url, "a".repeat(64))).isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> client.connect("http://127.0.0.1:8001", "weak")).isInstanceOf(IllegalArgumentException.class);
    assertThatCode(() -> client.connect("http://127.0.0.1:8001", "a".repeat(64))).doesNotThrowAnyException();
  }
}
