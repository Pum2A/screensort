package pl.pum.screensort.gallery.reading;

import pl.pum.screensort.api.ApiExceptionHandler;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ScanJobControllerTest {
  private final ScanJobManager jobs = mock(ScanJobManager.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new ScanRestController(jobs)).setControllerAdvice(new ApiExceptionHandler())
          // Tak jak w Boot: polskie komunikaty tekstowe i JSON w UTF-8.
          .setMessageConverters(
              new StringHttpMessageConverter(StandardCharsets.UTF_8),
              new JacksonJsonHttpMessageConverter())
          .build();
  private static final String REQUEST = "{\"folderPath\":\"demo-images\"}";

  @Test
  void returnsAcceptedWithIdAndLocationAndAllowsReadingTheSnapshot() throws Exception {
    UUID id = UUID.randomUUID();
    ScanJob job =
        new ScanJob(id, "demo-images", ScanStatus.PREPARING, new ScanSummary(0, 0, 0, 0), null);
    when(jobs.start(Path.of("demo-images"))).thenReturn(job);
    when(jobs.find(id)).thenReturn(Optional.of(job));
    mvc.perform(post("/api/scan/jobs").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
        .andExpect(status().isAccepted())
        .andExpect(header().string("Location", "/api/scan/jobs/" + id))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.status").value("PREPARING"))
        .andExpect(jsonPath("$.summary.newImages").value(0));
    mvc.perform(get("/api/scan/jobs/" + id))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.id").value(id.toString()));
  }

  @Test
  void unknownAndMalformedIdsHaveDifferentResponses() throws Exception {
    UUID unknown = UUID.randomUUID();
    when(jobs.find(unknown)).thenReturn(Optional.empty());
    mvc.perform(get("/api/scan/jobs/" + unknown))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(get("/api/scan/jobs/not-a-uuid")).andExpect(status().isBadRequest());
  }

  @Test
  void rejectsEmptyOrInvalidPathsBeforeCreatingAJob() throws Exception {
    mvc.perform(
            post("/api/scan/jobs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"folderPath\":\" \"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/scan/jobs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"folderPath\":\"\\u0000\"}"))
        .andExpect(status().isBadRequest());
    verify(jobs, never()).start(any());
  }

  @Test
  void returnsRejectionSampleOnlyForCompletedJobsWithoutStartingAnotherScan() throws Exception {
    UUID id = UUID.randomUUID();
    when(jobs.find(id)).thenReturn(Optional.of(new ScanJob(id, "demo-images",
        ScanStatus.COMPLETED, new ScanSummary(0, 0, 0, 125), null)));
    when(jobs.findRejections(id)).thenReturn(Optional.of(List.of(new RejectedFile(
        Path.of("demo-images/notes.txt"), "notes.txt", RejectionReason.UNSUPPORTED_EXTENSION))));
    mvc.perform(get("/api/scan/jobs/" + id + "/rejections"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.files[0].originalName").value("notes.txt"))
        .andExpect(jsonPath("$.files[0].reason").value("UNSUPPORTED_EXTENSION"))
        .andExpect(jsonPath("$.totalRejected").value(125))
        .andExpect(jsonPath("$.truncated").value(true));
    verify(jobs, never()).start(any());
  }

  @Test
  void distinguishesMissingAndUnfinishedRejectionResults() throws Exception {
    UUID id = UUID.randomUUID();
    when(jobs.find(id)).thenReturn(Optional.empty());
    mvc.perform(get("/api/scan/jobs/" + id + "/rejections")).andExpect(status().isNotFound());
    when(jobs.find(id)).thenReturn(Optional.of(new ScanJob(id, "demo-images",
        ScanStatus.SAVING, new ScanSummary(0, 0, 0, 1), null)));
    mvc.perform(get("/api/scan/jobs/" + id + "/rejections")).andExpect(status().isConflict());
    when(jobs.find(id)).thenReturn(Optional.of(new ScanJob(id, "demo-images",
        ScanStatus.COMPLETED, new ScanSummary(0, 0, 0, 0), null)));
    when(jobs.findRejections(id)).thenReturn(Optional.of(List.of()));
    mvc.perform(get("/api/scan/jobs/" + id + "/rejections"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.files").isEmpty())
        .andExpect(jsonPath("$.truncated").value(false));
  }

  @Test
  void returnsConflictWhenAnotherScanIsRunning() throws Exception {
    when(jobs.start(any())).thenThrow(new ScanAlreadyRunningException());
    mvc.perform(post("/api/scan/jobs").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/scan").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
        .andExpect(status().isNotFound());
  }

  @Test
  void reportsUnavailableExecutorWithoutLeakingItsInternalMessage() throws Exception {
    when(jobs.start(any())).thenThrow(new TaskRejectedException("Internal executor details"));
    mvc.perform(post("/api/scan/jobs").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("Nie można teraz uruchomić skanu. Spróbuj ponownie później."));
  }
}
