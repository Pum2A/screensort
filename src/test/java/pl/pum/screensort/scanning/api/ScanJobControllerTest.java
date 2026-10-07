package pl.pum.screensort.scanning.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.pum.screensort.scanning.jobs.ScanAlreadyRunningException;
import pl.pum.screensort.scanning.jobs.ScanJob;
import pl.pum.screensort.scanning.jobs.ScanJobManager;
import pl.pum.screensort.scanning.jobs.ScanStatus;
import pl.pum.screensort.scanning.result.ScanSummary;

class ScanJobControllerTest {
  private final ScanJobManager jobs = mock(ScanJobManager.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new ScanRestController(jobs))
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
  void returnsConflictForBackgroundAndLegacyRequestsWhenBusy() throws Exception {
    when(jobs.start(any())).thenThrow(new ScanAlreadyRunningException());
    when(jobs.scanAndSave(any())).thenThrow(new ScanAlreadyRunningException());
    mvc.perform(post("/api/scan/jobs").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/scan").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
        .andExpect(status().isConflict());
  }

  @Test
  void reportsUnavailableExecutorWithoutLeakingItsInternalMessage() throws Exception {
    when(jobs.start(any())).thenThrow(new TaskRejectedException("Internal executor details"));
    mvc.perform(post("/api/scan/jobs").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().string("Nie można teraz uruchomić skanu. Spróbuj ponownie później."));
  }
}
