package pl.pum.screensort.gallery;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import pl.pum.screensort.api.ApiExceptionHandler;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CatalogRestControllerTest {

  private static MockMvc mockMvc(CatalogReader reader) {
    return MockMvcBuilders.standaloneSetup(new CatalogRestController(reader))
        .setControllerAdvice(new ApiExceptionHandler())
        .build();
  }

  @Test
  void invalidPathIsRejectedAsBadRequestInsteadOfServerError() throws Exception {
    var reader = mock(CatalogReader.class);
    when(reader.read(anyString())).thenThrow(new InvalidPathException("zly", "zly"));

    mockMvc(reader)
        .perform(get("/api/catalog/files").param("folderPath", "zly"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Ścieżka ma niepoprawny format."));
  }

  @Test
  void catalogTooLargeForUnpaginatedReadIsRejectedAsBadRequest() throws Exception {
    var reader = mock(CatalogReader.class);
    when(reader.read(anyString()))
        .thenThrow(new CatalogTooLargeException("Katalog ma więcej niż 1000 obrazów."));

    mockMvc(reader)
        .perform(get("/api/catalog/files").param("folderPath", "folder"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Katalog ma więcej niż 1000 obrazów."));
  }

  @Test
  void ioFailureReturnsFixedMessageWithoutLeakingThePath() throws Exception {
    var reader = mock(CatalogReader.class);
    when(reader.read(anyString())).thenThrow(new IOException("synthetic-secret-folder-name"));

    mockMvc(reader)
        .perform(get("/api/catalog/files").param("folderPath", "folder"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("Nie można odczytać wybranego folderu."));
  }
}
