package pl.pum.screensort.analysis.client;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class PythonAnalysisClientConnectionTest {

  @Test
  void reconnectingReplacesAndClosesThePreviousHttpClient() {
    var client =
        new PythonAnalysisClient(
            RestClient.builder(), JsonMapper.builder().build(), "http://127.0.0.1:8001", "");
    client.connect("http://127.0.0.1:8001", "a".repeat(64));
    var first = client.currentHttpClient();
    client.connect("http://127.0.0.1:8001", "b".repeat(64));
    var second = client.currentHttpClient();

    assertThat(second).isNotSameAs(first);
    // Zamknięty klient nie obsługuje już nowych żądań: nie zostawia wątku selektora.
    assertThatThrownBy(
            () ->
                first.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:8001/health")).GET().build(),
                    HttpResponse.BodyHandlers.discarding()))
        .isInstanceOf(IOException.class);
  }
}
