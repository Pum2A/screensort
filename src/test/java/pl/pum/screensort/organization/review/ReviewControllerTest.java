package pl.pum.screensort.organization.review;

import pl.pum.screensort.api.ApiExceptionHandler;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.pum.screensort.naming.NamingException;

class ReviewControllerTest {
  private final UUID batch = UUID.randomUUID();
  private final String url = "/api/organization/batches/" + batch + "/items/1/review";
  private final ReviewService service = mock(ReviewService.class);
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ReviewController(service)).setControllerAdvice(new ApiExceptionHandler()).build();

  @Test void readsAndWritesJsonWithoutCaching() throws Exception {
    var result = ReviewServiceTest.item("COMPLETED", true, -1, null);
    var request = new ReviewService.Request(8L, "a".repeat(64), -1L, -1L, "SKIPPED", null, null);
    when(service.read(batch, 1)).thenReturn(result);
    when(service.save(batch, 1, request)).thenReturn(result);
    mvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.analysisId").value(8)).andExpect(jsonPath("$.namingRevision").value(-1));
    mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("""
        {"analysisId":8,"sourceSha256":"%s","reviewRevision":-1,"namingRevision":-1,"decision":"SKIPPED"}
        """.formatted("a".repeat(64))))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    verify(service).save(batch, 1, request);
    mvc.perform(post(url)).andExpect(status().isMethodNotAllowed());
    mvc.perform(get(url.replace(batch.toString(), "not-a-uuid"))).andExpect(status().isBadRequest());
  }

  @Test void returnsBothReviewAndNamingFailuresAsTypedUserErrors() throws Exception {
    when(service.save(eq(batch), eq(1L), any())).thenThrow(new ReviewException(409, "REVIEW_CHANGED", "Odśwież formularz."));
    mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVIEW_CHANGED"))
        .andExpect(header().string("Cache-Control", "no-store"));
    when(service.save(eq(batch), eq(1L), any())).thenThrow(new NamingException(422, "INVALID_NAME", "Popraw nazwę."));
    mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_NAME"));
  }
}
