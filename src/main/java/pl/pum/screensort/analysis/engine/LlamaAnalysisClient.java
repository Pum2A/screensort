package pl.pum.screensort.analysis.engine;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.analysis.AnalysisResult;
import pl.pum.screensort.analysis.AnalysisResult.Status;
import pl.pum.screensort.analysis.AnalysisResult.Subject;
import pl.pum.screensort.files.Sha256;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks a local llama-server (OpenAI-compatible API, Qwen vision model) what a screenshot shows.
 * The answer is constrained by a JSON schema, so the model can only return an app, a topic in
 * Polish and English, and one category from {@link Subject#CATEGORIES}. It reads no text, so OCR
 * is always reported as skipped; {@link FallbackAnalysisEngine} asks Python for the text.
 */
@Service
public class LlamaAnalysisClient implements AnalysisEngine {
  /** Vision models see a fixed token budget anyway; a smaller image only makes the upload faster. */
  static final int MAX_EDGE = 1024;
  private static final long MAX_PIXELS = 40_000_000;
  private static final int MAX_RESULT_BYTES = 256 * 1024;
  private static final String PROMPT = """
      Co widać na tym zrzucie ekranu? Odpowiedz JSON-em.
      category: Gry (gra), Czaty (komunikator, rozmowa), Dokumenty (faktura, umowa, PDF, tekst), \
      Programowanie (kod, terminal, IDE), Strony (strona WWW, artykuł), Social (media społecznościowe), \
      Media (film, muzyka, stream), Zakupy (sklep, koszyk, zamówienie), Mapy (mapa, trasa), \
      Zdjęcia (zdjęcie z aparatu), Inne.
      app: tylko nazwa aplikacji, strony lub gry, 1-3 słowa, bez nawiasów i bez zgadywania. \
      Przykłady: Discord, Visual Studio Code, Roblox, YouTube. Gdy nazwy nie widać, jedno słowo: \
      gra, przeglądarka, sklep, czat, folder.
      topic_pl: czego dotyczy ekran, 2-4 słowa po polsku.
      topic_en: to samo, 2-4 słowa po angielsku.""";
  private static final Map<String, Object> SCHEMA = Map.of(
      "type", "object",
      "properties", Map.of(
          "category", Map.of("type", "string", "enum", Subject.CATEGORIES),
          "app", Map.of("type", "string", "maxLength", 60),
          "topic_pl", Map.of("type", "string", "maxLength", 80),
          "topic_en", Map.of("type", "string", "maxLength", 80)),
      "required", List.of("category", "app", "topic_pl", "topic_en"));

  private final RestClient.Builder builder;
  private final JsonMapper json;
  private volatile Endpoint endpoint;
  private HttpClient http;
  private record Endpoint(RestClient client, RestClient extendedClient, String token, String model,
      String device) {}

  public LlamaAnalysisClient(RestClient.Builder builder, JsonMapper json,
      @Value("${screensort.analysis.llama.url:}") String url,
      @Value("${screensort.analysis.llama.model:qwen3.5-4b}") String model,
      @Value("${screensort.analysis.llama.device:gpu}") String device) {
    this.builder = builder;
    this.json = json;
    // A developer's own llama-server needs no key; the desktop app always sends one.
    if (!url.isBlank()) connect(url, "", model, device);
  }

  /** Called by the authenticated native launcher once llama-server is ready, never by the webview. */
  public void connect(String url, String token, String model, String device) {
    if (!token.isEmpty() && !token.matches("[A-Za-z0-9_-]{43,128}")) {
      throw new IllegalArgumentException("Weak llama-server key");
    }
    open(url, token, model, device);
  }

  private synchronized void open(String url, String token, String model, String device) {
    URI address = URI.create(url);
    if (!"http".equals(address.getScheme()) || !"127.0.0.1".equals(address.getHost())
        || address.getRawUserInfo() != null || address.getQuery() != null
        || address.getFragment() != null || address.getPort() <= 0
        || !(address.getPath().isEmpty() || address.getPath().equals("/"))
        || !model.matches("[a-z0-9.-]{1,40}") || !Set.of("cpu", "gpu").contains(device)) {
      throw new IllegalArgumentException("llama-server must use IPv4 loopback");
    }
    var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    var factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofSeconds(135));
    var extendedFactory = new JdkClientHttpRequestFactory(client);
    extendedFactory.setReadTimeout(Duration.ofSeconds(PythonAnalysisClient.EXTENDED_TIMEOUT_SECONDS + 15));
    var previous = http;
    http = client;
    endpoint = new Endpoint(builder.clone().baseUrl(url).requestFactory(factory).build(),
        builder.clone().baseUrl(url).requestFactory(extendedFactory).build(), token, model, device);
    // shutdown(), not close(): an analysis still running on the old server must not block the launcher.
    if (previous != null) previous.shutdown();
  }

  public boolean isConfigured() { return endpoint != null; }

  @PreDestroy
  synchronized void close() {
    endpoint = null;
    if (http != null) {
      http.close();
      http = null;
    }
  }

  @Override
  public AnalysisResult analyze(byte[] data, String mediaType, boolean ocr, boolean visual, boolean extendedTime) {
    Endpoint current = endpoint;
    if (current == null) throw new AnalysisException(503, "Silnik Qwen nie jest uruchomiony.");
    String hash = Sha256.hex(data);
    var ocrStep = new AnalysisResult.OcrStep(Status.SKIPPED, null, null);
    if (!visual) {
      return new AnalysisResult(hash, ocrStep, new AnalysisResult.VisualStep(Status.SKIPPED, null, null));
    }
    long started = System.nanoTime();
    String image = Base64.getEncoder().encodeToString(downscale(data));
    Subject subject = ask(current, extendedTime, image);
    double durationMs = (System.nanoTime() - started) / 1_000_000.0;
    var result = new AnalysisResult.Visual(subject.app() + ": " + subject.topicEn(), current.model(),
        "gguf-q4_k_m", "screenshot", current.device(), durationMs, subject);
    return new AnalysisResult(hash, ocrStep, new AnalysisResult.VisualStep(Status.COMPLETED, result, null));
  }

  private Subject ask(Endpoint current, boolean extendedTime, String image) {
    RestClient target = extendedTime ? current.extendedClient() : current.client();
    var body = Map.of(
        "temperature", 0,
        "max_tokens", 256,
        "chat_template_kwargs", Map.of("enable_thinking", false),
        "response_format", Map.of("type", "json_schema",
            "json_schema", Map.of("name", "screenshot", "schema", SCHEMA)),
        "messages", List.of(Map.of("role", "user", "content", List.of(
            Map.of("type", "image_url", "image_url", Map.of("url", "data:image/jpeg;base64," + image)),
            Map.of("type", "text", "text", PROMPT)))));
    try {
      return target.post().uri("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON)
          .headers(headers -> {
            if (!current.token().isEmpty()) headers.setBearerAuth(current.token());
          })
          .body(json.writeValueAsBytes(body))
          .exchange((request, response) -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
              throw PythonAnalysisClient.failure(response.getStatusCode().value(),
                  response.getHeaders().getFirst("Retry-After"));
            }
            byte[] bytes = response.getBody().readNBytes(MAX_RESULT_BYTES + 1);
            if (bytes.length > MAX_RESULT_BYTES) throw new AnalysisException(502, "Wynik analizy przekroczył limit.");
            return subject(bytes);
          });
    } catch (AnalysisException e) {
      throw e;
    } catch (RuntimeException e) {
      if (timedOut(e)) throw new AnalysisException(504, PythonAnalysisClient.TIMEOUT);
      // Do not expose model output, HTTP headers or local paths.
      throw new AnalysisException(503, "Nie udało się otrzymać poprawnego wyniku analizy.");
    }
  }

  /**
   * A malformed answer is 502, not 503: the same image would get the same answer again, so it must
   * fail that file instead of pausing the folder or switching engines.
   */
  private Subject subject(byte[] response) {
    try {
      JsonNode content = json.readTree(response).path("choices").path(0).path("message").path("content");
      if (!content.isString()) throw new AnalysisException(502, "Silnik Qwen zwrócił pustą odpowiedź.");
      JsonNode answer = json.readTree(content.asString());
      return new Subject(clean(answer, "app"), clean(answer, "topic_pl"), clean(answer, "topic_en"),
          answer.path("category").asString(""));
    } catch (AnalysisException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new AnalysisException(502, "Silnik Qwen zwrócił niepełny opis obrazu.");
    }
  }

  private static String clean(JsonNode answer, String field) {
    return answer.path(field).asString("").strip();
  }

  /** Reads only every n-th pixel of a large image, so a 4K screenshot never sits in memory whole. */
  static byte[] downscale(byte[] data) {
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      var readers = input == null ? null : ImageIO.getImageReaders(input);
      if (readers == null || !readers.hasNext()) throw badImage();
      var reader = readers.next();
      try {
        reader.setInput(input, true, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) throw badImage();
        int step = Math.max(1, Math.max(width, height) / MAX_EDGE);
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceSubsampling(step, step, 0, 0);
        return jpeg(fit(reader.read(0, param)));
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof AnalysisException analysis) throw analysis;
      throw badImage();
    }
  }

  private static BufferedImage fit(BufferedImage source) {
    double scale = Math.min(1.0, (double) MAX_EDGE / Math.max(source.getWidth(), source.getHeight()));
    int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
    int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
    var target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = target.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      // Transparent pixels become white, as they look in most viewers.
      graphics.setColor(java.awt.Color.WHITE);
      graphics.fillRect(0, 0, width, height);
      graphics.drawImage(source, 0, 0, width, height, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private static byte[] jpeg(BufferedImage image) throws IOException {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    var out = new ByteArrayOutputStream();
    try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(output);
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(0.85f);
      writer.write(null, new IIOImage(image, null, null), param);
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  private static AnalysisException badImage() {
    return new AnalysisException(422, PythonAnalysisClient.BAD_IMAGE);
  }


  private static boolean timedOut(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) return true;
    }
    return false;
  }
}
