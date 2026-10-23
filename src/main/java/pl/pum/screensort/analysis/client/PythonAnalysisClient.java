package pl.pum.screensort.analysis.client;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.analysis.EngineBusyException;
import pl.pum.screensort.analysis.result.AnalysisResult;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PythonAnalysisClient {
  static final int EXTENDED_TIMEOUT_SECONDS = 240;
  static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(2);
  static final String BAD_IMAGE = "Obraz jest uszkodzony, za duży albo ma nieobsługiwany format. Pominięty.";
  static final String TIMEOUT = "Analiza obrazu trwała zbyt długo.";
  private static final String UNAVAILABLE = "Analiza jest zajęta lub niedostępna. Spróbuj ponownie później.";
  private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(30);
  private static final int MAX_RESULT_BYTES = 2 * 1024 * 1024;
  private final RestClient.Builder builder;
  private volatile Endpoint endpoint;
  private final JsonMapper json;
  private HttpClient http;
  private HttpClient extendedHttp;
  private record Endpoint(RestClient client, RestClient extendedClient, String token) {}

  public PythonAnalysisClient(RestClient.Builder builder, JsonMapper json,
      @Value("${screensort.analysis.url:http://127.0.0.1:8001}") String url,
      @Value("${screensort.analysis.token:}") String token) {
    this.builder = builder;
    this.json = json;
    if (!token.isBlank()) connect(url, token);
  }

  /** Called only by the authenticated native launcher, never by the webview. */
  public synchronized void connect(String url, String token) {
    URI address = URI.create(url);
    if (!"http".equals(address.getScheme()) || !"127.0.0.1".equals(address.getHost())
        || address.getRawUserInfo() != null || address.getQuery() != null
        || address.getFragment() != null || address.getPort() <= 0
        || !(address.getPath().isEmpty() || address.getPath().equals("/"))
        || !token.matches("[A-Za-z0-9_-]{43,128}")) {
      throw new IllegalArgumentException("Analysis service must use IPv4 loopback");
    }
    var client = newHttpClient();
    var factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofSeconds(135));
    // A second try after a timeout asks Python for a longer limit, so the read waits longer too.
    var extendedClient = newHttpClient();
    var extendedFactory = new JdkClientHttpRequestFactory(extendedClient);
    extendedFactory.setReadTimeout(Duration.ofSeconds(EXTENDED_TIMEOUT_SECONDS + 15));
    var previous = http;
    var previousExtended = extendedHttp;
    http = client;
    extendedHttp = extendedClient;
    endpoint = new Endpoint(builder.clone().baseUrl(url).requestFactory(factory).build(),
        builder.clone().baseUrl(url).requestFactory(extendedFactory).build(), token);
    // Każdy HttpClient trzyma wątek selektora i pulę wątków. Poprzedni musi zostać zamknięty.
    if (previous != null) previous.close();
    if (previousExtended != null) previousExtended.close();
  }

  private static HttpClient newHttpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();
  }

  /** Tylko do testów: aktualnie używany klient HTTP. */
  HttpClient currentHttpClient() { return http; }

  @PreDestroy
  synchronized void close() {
    if (http != null) {
      http.close();
      http = null;
    }
    if (extendedHttp != null) {
      extendedHttp.close();
      extendedHttp = null;
    }
  }

  public AnalysisResult analyze(byte[] data, String mediaType, boolean ocr, boolean visual) {
    return analyze(data, mediaType, ocr, visual, false);
  }

  /** {@code extendedTime} asks for a longer inference limit; used once for a file that timed out. */
  public AnalysisResult analyze(byte[] data, String mediaType, boolean ocr, boolean visual, boolean extendedTime) {
    Endpoint current = endpoint;
    if (current == null) throw new AnalysisException(503, "Najpierw uruchom pakiet analizy w aplikacji.");
    RestClient client = extendedTime ? current.extendedClient() : current.client();
    try {
      return client.post().uri(uri -> {
            uri.path("/api/v1/analyses").queryParam("ocr", ocr).queryParam("visual", visual);
            if (extendedTime) uri.queryParam("timeoutSeconds", EXTENDED_TIMEOUT_SECONDS);
            return uri.build();
          })
          .headers(headers -> headers.setBearerAuth(current.token()))
          .contentType(MediaType.parseMediaType(mediaType)).body(data)
          .exchange((request, response) -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
              throw failure(response.getStatusCode().value(), response.getHeaders().getFirst("Retry-After"));
            }
            byte[] bytes = response.getBody().readNBytes(MAX_RESULT_BYTES + 1);
            if (bytes.length > MAX_RESULT_BYTES) throw new AnalysisException(502, "Wynik analizy przekroczył limit.");
            return json.readValue(bytes, AnalysisResult.class);
          });
    } catch (AnalysisException e) {
      throw e;
    } catch (RuntimeException e) {
      if (timedOut(e)) throw new AnalysisException(504, TIMEOUT);
      // Do not expose HTTP headers, raw OCR, Python errors or local paths.
      throw new AnalysisException(503, "Nie udało się otrzymać poprawnego wyniku analizy.");
    }
  }

  /**
   * Python's answer decides what a folder analysis does next: a bad image fails only that file,
   * a timeout gets one more try later, a busy engine is waited for, anything else pauses the batch.
   */
  static AnalysisException failure(int status, String retryAfter) {
    return switch (status) {
      case 400, 413, 415, 422 -> new AnalysisException(422, BAD_IMAGE);
      case 408, 504 -> new AnalysisException(504, TIMEOUT);
      case 429 -> new EngineBusyException(retryAfter(retryAfter));
      case 503 -> retryAfter == null
          ? new AnalysisException(503, UNAVAILABLE)
          : new EngineBusyException(retryAfter(retryAfter));
      default -> new AnalysisException(503, UNAVAILABLE);
    };
  }

  private static Duration retryAfter(String header) {
    if (header == null || !header.matches("[0-9]{1,4}")) return DEFAULT_RETRY_AFTER;
    Duration value = Duration.ofSeconds(Long.parseLong(header));
    return value.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : value;
  }

  private static boolean timedOut(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) return true;
    }
    return false;
  }
}
