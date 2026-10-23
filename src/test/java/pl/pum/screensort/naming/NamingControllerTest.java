package pl.pum.screensort.naming;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.pum.screensort.naming.api.NamingController;
import pl.pum.screensort.naming.api.dto.NamingReviewDto;

class NamingControllerTest {
  private final NamingService service = mock(NamingService.class);
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new NamingController(service)).build();

  @Test
  void exposesTheReadAndApprovalWithoutCachingOrStartingAnalysis() throws Exception {
    var result = new NamingReviewDto(1, 8, "a".repeat(64), "a.png", "mug.png", false, true, null, null, false, -1);
    var approved = new NamingReviewDto(1, 8, "a".repeat(64), "a.png", "mug.png", false, true,
        "moj_kubek.png", Instant.parse("2026-10-18T12:00:00Z"), true, 0);
    when(service.read(1)).thenReturn(result);
    when(service.approve(1, 8, "a".repeat(64), -1, "moj_kubek.png")).thenReturn(approved);
    mvc.perform(get("/api/catalog/files/1/naming"))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.proposedName").value("mug.png"))
        .andExpect(jsonPath("$.proposalUncertain").value(false));
    mvc.perform(put("/api/catalog/files/1/naming").contentType(MediaType.APPLICATION_JSON)
        .content("{\"analysisId\":8,\"sourceSha256\":\"" + "a".repeat(64)
            + "\",\"revision\":-1,\"name\":\"moj_kubek.png\"}"))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.approvedName").value("moj_kubek.png"))
        .andExpect(jsonPath("$.approvedAt").value("2026-10-18T12:00:00Z"))
        .andExpect(jsonPath("$.revision").value(0));
    verify(service).approve(1, 8, "a".repeat(64), -1, "moj_kubek.png");
  }

  @Test
  void distinguishesAConflictAndAValidationFailure() throws Exception {
    when(service.read(1)).thenThrow(new NamingException(409, "ANALYSIS_REQUIRED", "Najpierw wykonaj analizę."));
    mvc.perform(get("/api/catalog/files/1/naming")).andExpect(status().isConflict())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value("ANALYSIS_REQUIRED"));
    when(service.approve(anyLong(), anyLong(), any(), anyLong(), any()))
        .thenThrow(new NamingException(422, "INVALID_NAME", "Nieprawidłowa nazwa."));
    mvc.perform(put("/api/catalog/files/1/naming").contentType(MediaType.APPLICATION_JSON)
        .content("{\"analysisId\":8,\"revision\":-1,\"name\":\"../bad.png\"}"))
        .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_NAME"));
    mvc.perform(get("/api/catalog/files/not-an-id/naming")).andExpect(status().isBadRequest());
  }
}
