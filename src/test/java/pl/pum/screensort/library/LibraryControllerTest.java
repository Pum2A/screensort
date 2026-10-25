package pl.pum.screensort.library;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.pum.screensort.catalog.reading.CatalogTooLargeException;

class LibraryControllerTest {

  private static MockMvc mockMvc(LibraryReader reader) {
    return MockMvcBuilders.standaloneSetup(new LibraryController(reader))
        .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
        .build();
  }

  @Test
  void ioFailureReturnsFixedMessageWithoutLeakingThePath() throws Exception {
    var reader = mock(LibraryReader.class);
    when(reader.read(anyString())).thenThrow(new IOException("synthetic-secret-folder-name"));

    mockMvc(reader)
        .perform(get("/api/library").param("folderPath", "folder"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().string("Nie można odczytać wybranego folderu."));
  }

  @Test
  void tooLargeFolderIsABadRequestWithAUserMessage() throws Exception {
    var reader = mock(LibraryReader.class);
    when(reader.read(anyString())).thenThrow(new CatalogTooLargeException("Użyj endpointu /api/catalog/page."));

    mockMvc(reader)
        .perform(get("/api/library").param("folderPath", "folder"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("Biblioteka pokazuje foldery do 1000 obrazów."));
  }
}
