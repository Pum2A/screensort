package pl.pum.screensort.analysis.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.analysis.result.AnalysisResult;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PythonAnalysisClient {
  private static final int MAX_RESULT_BYTES = 2 * 1024 * 1024;
  private final RestClient.Builder builder;
  private volatile Endpoint endpoint;
  private final JsonMapper json;
  private record Endpoint(RestClient client, String token) {}

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
    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofSeconds(135));
    endpoint = new Endpoint(builder.clone().baseUrl(url).requestFactory(factory).build(), token);
  }

  public AnalysisResult analyze(byte[] data, String mediaType, boolean ocr, boolean visual) {
    Endpoint current = endpoint;
    if (current == null) throw new AnalysisException(503, "Najpierw uruchom pakiet analizy w aplikacji.");
    try {
      return current.client().post().uri(uri -> uri.path("/api/v1/analyses")
              .queryParam("ocr", ocr).queryParam("visual", visual).build())
          .headers(headers -> headers.setBearerAuth(current.token()))
          .contentType(MediaType.parseMediaType(mediaType)).body(data)
          .exchange((request, response) -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
              throw new AnalysisException(503, "Analiza jest zajęta lub niedostępna. Spróbuj ponownie później.");
            }
            byte[] bytes = response.getBody().readNBytes(MAX_RESULT_BYTES + 1);
            if (bytes.length > MAX_RESULT_BYTES) throw new AnalysisException(502, "Wynik analizy przekroczył limit.");
            return json.readValue(bytes, AnalysisResult.class);
          });
    } catch (AnalysisException e) {
      throw e;
    } catch (RuntimeException e) {
      // Do not expose HTTP headers, raw OCR, Python errors or local paths.
      throw new AnalysisException(503, "Nie udało się otrzymać poprawnego wyniku analizy.");
    }
  }
}
