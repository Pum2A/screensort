package pl.pum.screensort.scanning.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import pl.pum.screensort.catalog.database.AllowedRootRepository;
import pl.pum.screensort.catalog.database.FileRecordRepository;
import pl.pum.screensort.catalog.writing.CatalogUpdater;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class ScanJobApiIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));

  @Autowired private MockMvc mvc;
  @Autowired private AllowedRootRepository roots;
  @Autowired private FileRecordRepository files;
  @Autowired @Qualifier("scanJobRunner") private ThreadPoolTaskExecutor runner;
  @MockitoSpyBean private CatalogUpdater updater;

  @Test
  @Timeout(40)
  void exposesSavingBeforeCompletionAndCommittedCatalogAfterwards(@TempDir Path folder)
      throws Exception {
    Path image = folder.resolve("photo.png");
    assertTrue(
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", image.toFile()));
    Files.writeString(folder.resolve("notes.txt"), "Not an image.");
    byte[] original = Files.readAllBytes(image);
    CountDownLatch savingStarted = new CountDownLatch(1);
    CountDownLatch allowSave = new CountDownLatch(1);
    // Zatrzymujemy tylko test przed SQL-em, żeby zobaczyć prawdziwy etap SAVING.
    doAnswer(
            invocation -> {
              savingStarted.countDown();
              if (!allowSave.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release saving");
              }
              return invocation.callRealMethod();
            })
        .when(updater).update(any(), any());

    String request = "{\"folderPath\":\"" + folder.toString().replace("\\", "\\\\") + "\"}";
    String location;
    try {
      location =
          mvc.perform(
                  post("/api/scan/jobs").contentType(MediaType.APPLICATION_JSON).content(request))
              .andExpect(status().isAccepted())
              .andExpect(jsonPath("$.status").value("PREPARING"))
              .andReturn().getResponse().getHeader("Location");
      assertThat(location).startsWith("/api/scan/jobs/");
      assertTrue(savingStarted.await(15, TimeUnit.SECONDS));
      mvc.perform(get(location))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SAVING"))
          .andExpect(jsonPath("$.summary.newImages").value(1))
          .andExpect(jsonPath("$.summary.rejectedFiles").value(1));
      assertThat(roots.findByCanonicalPath(folder.toRealPath().toString())).isEmpty();
      mvc.perform(get(location + "/rejections")).andExpect(status().isConflict());
      mvc.perform(
              post("/api/scan/jobs").contentType(MediaType.APPLICATION_JSON)
                  .content("{\"folderPath\":\"another-folder\"}"))
          .andExpect(status().isConflict());
      mvc.perform(post("/api/scan").contentType(MediaType.APPLICATION_JSON).content(request))
          .andExpect(status().isConflict());
      mvc.perform(post("/scan").param("folderPath", folder.toString()))
          .andExpect(status().isOk())
          .andExpect(
              model().attribute(
                  "errorMessage", "Inny skan jest już w toku. Poczekaj na jego zakończenie."));
    } finally {
      allowSave.countDown();
    }

    // Bariera w tej samej jedno-wątkowej puli: wcześniejsze zadanie musi już wrócić.
    runner.submit(() -> {}).get(15, TimeUnit.SECONDS);
    mvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.summary.newImages").value(1))
        .andExpect(jsonPath("$.summary.rejectedFiles").value(1));
    var root = roots.findByCanonicalPath(folder.toRealPath().toString()).orElseThrow();
    assertThat(files.findByAllowedRootAndRelativePath(root, "photo.png")).isPresent();
    mvc.perform(get("/api/catalog/page").param("folderPath", folder.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalFiles").value(1));
    // Search covers the whole folder in the database, any case, and treats % as a plain character.
    mvc.perform(get("/api/catalog/page").param("folderPath", folder.toString()).param("search", " PHOTO "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalFiles").value(1))
        .andExpect(jsonPath("$.files[0].originalName").value("photo.png"));
    mvc.perform(get("/api/catalog/page").param("folderPath", folder.toString()).param("search", "%"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalFiles").value(0));
    mvc.perform(get(location + "/rejections"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRejected").value(1))
        .andExpect(jsonPath("$.files[0].reason").value("UNSUPPORTED_EXTENSION"))
        .andExpect(jsonPath("$.truncated").value(false));
    assertThat(Files.readAllBytes(image)).isEqualTo(original);
  }
}
