package pl.pum.screensort.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import pl.pum.screensort.analysis.AnalysisException;

class ApiExceptionHandlerTest {
  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test void everyAnswerIsApiErrorJsonThatIsNeverCached() {
    var response = handler.rejected(new ApiException(400, "INVALID_PAGE", "Zła strona."));
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isEqualTo(new ApiError("INVALID_PAGE", "Zła strona."));
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
  }

  @Test void analysisFailuresGetACodeFromTheirStatus() {
    var response = handler.analysis(new AnalysisException(504, "Za długo."));
    assertThat(response.getStatusCode().value()).isEqualTo(504);
    assertThat(response.getBody()).isEqualTo(new ApiError("ANALYSIS_TIMEOUT", "Za długo."));
    assertThat(ApiExceptionHandler.analysisCode(422)).isEqualTo("INVALID_IMAGE");
    assertThat(ApiExceptionHandler.analysisCode(418)).isEqualTo("ANALYSIS_FAILED");
  }

  @Test void pathsAndInternalMessagesNeverReachTheBody() {
    String path = "C:/Users/owner/Pictures/private";
    var invalid = handler.invalidPath(new InvalidPathException(path, "bad char"));
    var unreadable = handler.unreadable(new IOException(path));
    var busy = handler.busy(new TaskRejectedException("pool-1 queue full " + path));
    for (var response : java.util.List.of(invalid, unreadable, busy)) {
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().message()).doesNotContain("private").doesNotContain("pool-1");
    }
    assertThat(invalid.getBody().code()).isEqualTo("INVALID_PATH");
    assertThat(unreadable.getStatusCode().value()).isEqualTo(500);
    assertThat(busy.getStatusCode().value()).isEqualTo(503);
  }
}
