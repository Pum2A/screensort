package pl.pum.screensort.analysis.client;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.analysis.EngineBusyException;
import tools.jackson.databind.json.JsonMapper;

class PythonAnalysisFailureTest {
  @ParameterizedTest
  @ValueSource(ints = {400, 413, 415, 422})
  void aRejectedImageFailsOnlyThatFile(int status) {
    var error = PythonAnalysisClient.failure(status, null);
    assertThat(error.status()).isEqualTo(422);
    assertThat(error).isNotInstanceOf(EngineBusyException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {408, 504})
  void aSlowAnalysisIsATimeout(int status) {
    assertThat(PythonAnalysisClient.failure(status, null).status()).isEqualTo(504);
  }

  @Test void busyEngineIsWaitedForWithABoundedPause() {
    assertThat(PythonAnalysisClient.failure(429, null))
        .isInstanceOfSatisfying(EngineBusyException.class,
            busy -> assertThat(busy.retryAfter()).isEqualTo(PythonAnalysisClient.DEFAULT_RETRY_AFTER));
    assertThat(PythonAnalysisClient.failure(503, "5"))
        .isInstanceOfSatisfying(EngineBusyException.class,
            busy -> assertThat(busy.retryAfter()).isEqualTo(Duration.ofSeconds(5)));
    assertThat(PythonAnalysisClient.failure(503, "9999"))
        .isInstanceOfSatisfying(EngineBusyException.class,
            busy -> assertThat(busy.retryAfter()).isEqualTo(Duration.ofSeconds(30)));
    assertThat(PythonAnalysisClient.failure(429, "soon"))
        .isInstanceOfSatisfying(EngineBusyException.class,
            busy -> assertThat(busy.retryAfter()).isEqualTo(PythonAnalysisClient.DEFAULT_RETRY_AFTER));
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 500, 503})
  void anythingElseMeansTheEngineIsUnavailable(int status) {
    var error = PythonAnalysisClient.failure(status, null);
    assertThat(error.status()).isEqualTo(503);
    assertThat(error).isNotInstanceOf(EngineBusyException.class);
  }

  @Test void realResponsesAreMappedAndTheExtendedTryAsksForMoreTime() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var queries = new java.util.concurrent.CopyOnWriteArrayList<String>();
    server.createContext("/api/v1/analyses", exchange -> {
      queries.add(exchange.getRequestURI().getQuery());
      exchange.getRequestBody().readAllBytes();
      boolean busy = exchange.getRequestURI().getQuery().contains("visual=false");
      if (busy) exchange.getResponseHeaders().add("Retry-After", "3");
      exchange.sendResponseHeaders(busy ? 503 : 422, -1);
      exchange.close();
    });
    server.start();
    var client = new PythonAnalysisClient(RestClient.builder(), JsonMapper.builder().build(), "http://127.0.0.1:8001", "");
    try {
      client.connect("http://127.0.0.1:" + server.getAddress().getPort(), "a".repeat(64));
      assertThatThrownBy(() -> client.analyze(new byte[] {1}, "image/png", true, true, true))
          .isInstanceOfSatisfying(AnalysisException.class, error -> assertThat(error.status()).isEqualTo(422));
      assertThatThrownBy(() -> client.analyze(new byte[] {1}, "image/png", true, false))
          .isInstanceOfSatisfying(EngineBusyException.class,
              busy -> assertThat(busy.retryAfter()).isEqualTo(Duration.ofSeconds(3)));
      assertThat(queries.get(0)).contains("timeoutSeconds=" + PythonAnalysisClient.EXTENDED_TIMEOUT_SECONDS);
      assertThat(queries.get(1)).doesNotContain("timeoutSeconds");
    } finally {
      client.close();
      server.stop(0);
    }
  }
}
