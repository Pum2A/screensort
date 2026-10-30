package pl.pum.screensort.analysis.engine;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.analysis.AnalysisResult.Status;
import pl.pum.screensort.files.Sha256;
import tools.jackson.databind.json.JsonMapper;

class LlamaAnalysisClientTest {
  private final JsonMapper json = JsonMapper.builder().build();

  @Test void staysOffWithoutAUrlAndRefusesAnythingButLoopback() {
    var off = client("");
    assertThat(off.isConfigured()).isFalse();
    assertThatThrownBy(() -> off.analyze(png(10, 10), "image/png", false, true, false))
        .isInstanceOfSatisfying(AnalysisException.class, error -> assertThat(error.status()).isEqualTo(503));
    for (String url : new String[]{"https://127.0.0.1:8080", "http://localhost:8080", "http://10.0.0.2:8080",
        "http://127.0.0.1:8080/v1", "http://127.0.0.1"}) {
      assertThatThrownBy(() -> client(url)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test void sendsASmallJpegWithTheSchemaAndReadsTheSubject() throws Exception {
    var body = new AtomicReference<String>();
    var server = server(200, """
        {"choices":[{"message":{"content":"{\\"category\\":\\"Programowanie\\",\\"app\\":\\" Visual Studio Code \\",\
        \\"topic_pl\\":\\"edycja kodu\\",\\"topic_en\\":\\"editing code\\"}"}}]}""", body);
    try {
      byte[] image = png(3000, 1500);
      var result = client(url(server)).analyze(image, "image/png", true, true, false);

      assertThat(result.sourceSha256()).isEqualTo(Sha256.hex(image));
      assertThat(result.ocr().status()).isEqualTo(Status.SKIPPED);
      var visual = result.visual().result();
      assertThat(visual.task()).isEqualTo("screenshot");
      assertThat(visual.subject().app()).isEqualTo("Visual Studio Code");
      assertThat(visual.subject().category()).isEqualTo("Programowanie");

      var request = json.readTree(body.get());
      assertThat(request.path("response_format").path("type").asString()).isEqualTo("json_schema");
      String url = request.path("messages").path(0).path("content").path(0).path("image_url").path("url").asString();
      assertThat(url).startsWith("data:image/jpeg;base64,");
      var sent = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1))));
      assertThat(Math.max(sent.getWidth(), sent.getHeight())).isLessThanOrEqualTo(LlamaAnalysisClient.MAX_EDGE);
    } finally {
      server.stop(0);
    }
  }

  @Test void theLauncherConnectsAtRuntimeWithAKeyAndSafeLabels() throws Exception {
    var headers = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/chat/completions", exchange -> {
      headers.set(exchange.getRequestHeaders().getFirst("Authorization"));
      exchange.getRequestBody().readAllBytes();
      String answer = "{\\\"category\\\":\\\"Gry\\\",\\\"app\\\":\\\"Roblox\\\",\\\"topic_pl\\\":\\\"lobby\\\","
          + "\\\"topic_en\\\":\\\"lobby\\\"}";
      byte[] bytes = ("{\"choices\":[{\"message\":{\"content\":\"" + answer + "\"}}]}").getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    try {
      var client = client("");
      String key = "k".repeat(64);
      assertThatThrownBy(() -> client.connect(url(server), "weak", "qwen3.5-4b", "gpu"))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> client.connect(url(server), key, "Qwen 4B", "gpu"))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> client.connect(url(server), key, "qwen3.5-4b", "cuda"))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(client.isConfigured()).isFalse();

      client.connect(url(server), key, "qwen3.5-2b", "cpu");
      var visual = client.analyze(png(10, 10), "image/png", false, true, false).visual().result();

      assertThat(headers.get()).isEqualTo("Bearer " + key);
      assertThat(visual.model()).isEqualTo("qwen3.5-2b");
      assertThat(visual.device()).isEqualTo("cpu");
    } finally {
      server.stop(0);
    }
  }

  @Test void skipsTheModelWhenNoVisualAnalysisIsAsked() {
    var result = client("http://127.0.0.1:9").analyze(png(10, 10), "image/png", true, false, false);
    assertThat(result.visual().status()).isEqualTo(Status.SKIPPED);
  }

  @Test void aBrokenImageFailsOnlyThatFile() {
    assertThatThrownBy(() -> client("http://127.0.0.1:9").analyze("not an image".getBytes(StandardCharsets.UTF_8),
        "image/png", false, true, false))
        .isInstanceOfSatisfying(AnalysisException.class, error -> assertThat(error.status()).isEqualTo(422));
  }

  @Test void anUnusableAnswerOrADeadServerIsReportedWithoutModelOutput() throws Exception {
    var server = server(200, """
        {"choices":[{"message":{"content":"{\\"category\\":\\"Kosmos\\",\\"app\\":\\"x\\",\
        \\"topic_pl\\":\\"y\\",\\"topic_en\\":\\"z\\"}"}}]}""", new AtomicReference<>());
    try {
      assertThatThrownBy(() -> client(url(server)).analyze(png(10, 10), "image/png", false, true, false))
          .isInstanceOfSatisfying(AnalysisException.class, error -> {
            assertThat(error.status()).isEqualTo(502);
            assertThat(error.getMessage()).doesNotContain("Kosmos");
          });
    } finally {
      server.stop(0);
    }
    var cut = server(200, """
        {"choices":[{"message":{"content":"{\\"category\\":\\"Gry\\",\\"app"}}]}""", new AtomicReference<>());
    try {
      // A truncated answer would come back the same next time, so it fails the file instead of pausing.
      assertThatThrownBy(() -> client(url(cut)).analyze(png(10, 10), "image/png", false, true, false))
          .isInstanceOfSatisfying(AnalysisException.class, error -> assertThat(error.status()).isEqualTo(502));
    } finally {
      cut.stop(0);
    }
    var dead = server(503, "{}", new AtomicReference<>());
    try {
      assertThatThrownBy(() -> client(url(dead)).analyze(png(10, 10), "image/png", false, true, false))
          .isInstanceOfSatisfying(AnalysisException.class, error -> assertThat(error.status()).isEqualTo(503));
    } finally {
      dead.stop(0);
    }
  }

  private LlamaAnalysisClient client(String url) {
    return new LlamaAnalysisClient(RestClient.builder(), json, url, "qwen3.5-2b", "gpu");
  }

  private static HttpServer server(int status, String response, AtomicReference<String> body) throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/chat/completions", exchange -> {
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    return server;
  }

  private static String url(HttpServer server) {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private static byte[] png(int width, int height) {
    try {
      var out = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
      return out.toByteArray();
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
