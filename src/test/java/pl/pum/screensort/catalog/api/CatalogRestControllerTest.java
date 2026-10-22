package pl.pum.screensort.catalog.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.pum.screensort.catalog.reading.CatalogReader;
import pl.pum.screensort.catalog.reading.CatalogTooLargeException;

class CatalogRestControllerTest {

  private static MockMvc mockMvc(CatalogReader reader) {
    // Spring Boot ustawia UTF-8 na StringHttpMessageConverter; samodzielny MockMvc tego nie robi.
    return MockMvcBuilders.standaloneSetup(new CatalogRestController(reader))
        .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
        .build();
  }

  @Test
  void invalidPathIsRejectedAsBadRequestInsteadOfServerError() throws Exception {
    var reader = mock(CatalogReader.class);
    when(reader.read(anyString())).thenThrow(new InvalidPathException("zly", "zly"));

    mockMvc(reader)
        .perform(get("/api/catalog/files").param("folderPath", "zly"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("Nieprawidłowa ścieżka folderu."));
  }

  @Test
  void catalogTooLargeForUnpaginatedReadIsRejectedAsBadRequest() throws Exception {
    var reader = mock(CatalogReader.class);
    when(reader.read(anyString()))
        .thenThrow(new CatalogTooLargeException("Katalog ma więcej niż 1000 obrazów."));

    mockMvc(reader)
        .perform(get("/api/catalog/files").param("folderPath", "folder"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("Katalog ma więcej niż 1000 obrazów."));
  }

  @Test
  void ioFailureReturnsFixedMessageWithoutLeakingThePath() throws Exception {
    var reader = mock(CatalogReader.class);
    when(reader.read(anyString())).thenThrow(new IOException("synthetic-secret-folder-name"));

    mockMvc(reader)
        .perform(get("/api/catalog/files").param("folderPath", "folder"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().string("Nie można odczytać wybranego folderu."));
  }
}
