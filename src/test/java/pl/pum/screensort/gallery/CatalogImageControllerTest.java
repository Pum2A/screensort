package pl.pum.screensort.gallery;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import pl.pum.screensort.api.ApiExceptionHandler;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.pum.screensort.gallery.preview.CatalogImagePreview;
import pl.pum.screensort.gallery.preview.ImagePreviewException;

class CatalogImageControllerTest {
  @Test
  void servesPngByRecordIdWithPrivateReadHeaders() throws Exception {
    var previews = mock(CatalogImagePreview.class);
    byte[] png = {1, 2, 3};
    when(previews.read(12, CatalogImagePreview.Size.THUMBNAIL)).thenReturn(png);
    when(previews.read(12, CatalogImagePreview.Size.PREVIEW)).thenReturn(png);
    var mvc = MockMvcBuilders.standaloneSetup(new CatalogImageController(previews)).setControllerAdvice(new ApiExceptionHandler()).build();
    for (String variant : new String[] {"thumbnail", "preview"}) {
      mvc.perform(get("/api/catalog/files/12/" + variant))
          .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
          .andExpect(header().string("Cache-Control", "no-store"))
          .andExpect(header().string("X-Content-Type-Options", "nosniff"))
          .andExpect(content().bytes(png));
    }
  }

  @ParameterizedTest
  @CsvSource({"NOT_FOUND,404", "UNSAFE_PATH,403", "TOO_LARGE,413", "INVALID_IMAGE,422", "CHANGED,409", "BUSY,503"})
  void mapsFailuresWithoutLeakingSourcePaths(ImagePreviewException.Reason reason, int expectedStatus) throws Exception {
    var previews = mock(CatalogImagePreview.class);
    when(previews.read(12, CatalogImagePreview.Size.THUMBNAIL))
        .thenThrow(new ImagePreviewException(reason, "Podgląd niedostępny."));
    var mvc = MockMvcBuilders.standaloneSetup(new CatalogImageController(previews)).setControllerAdvice(new ApiExceptionHandler()).build();
    var response = mvc.perform(get("/api/catalog/files/12/thumbnail"))
        .andExpect(status().is(expectedStatus)).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.message").value("Podgląd niedostępny."));
    if (reason == ImagePreviewException.Reason.BUSY) response.andExpect(header().string("Retry-After", "1"));
  }
}
