package pl.pum.screensort.scanning.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import pl.pum.screensort.catalog.database.AllowedRootEntity;
import pl.pum.screensort.catalog.database.AllowedRootRepository;
import pl.pum.screensort.catalog.database.FileRecordEntity;
import pl.pum.screensort.catalog.database.FileRecordRepository;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class ScanRestControllerIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));

  @Autowired private MockMvc mockMvc;
  @Autowired private AllowedRootRepository allowedRootRepository;
  @Autowired private FileRecordRepository fileRecordRepository;

  @Test
  void webScansRealImageAndKeepsOneDatabaseRecordAfterRescan(@TempDir Path folder)
      throws Exception {
    Path imagePath = folder.resolve("photo.png");
    assertThat(
            ImageIO.write(
                new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", imagePath.toFile()))
        .isTrue();
    String requestJson = "{\"folderPath\":\"" + folder.toString().replace("\\", "\\\\") + "\"}";

    mockMvc.perform(get("/api/catalog/files").param("folderPath", folder.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    mockMvc.perform(post("/api/scan").contentType(MediaType.APPLICATION_JSON).content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted.length()").value(1));

    AllowedRootEntity root =
        allowedRootRepository.findByCanonicalPath(folder.toRealPath().toString()).orElseThrow();
    FileRecordEntity firstRecord =
        fileRecordRepository.findByAllowedRootAndRelativePath(root, "photo.png").orElseThrow();
    Long firstId = firstRecord.getId();

    mockMvc.perform(get("/api/catalog/files").param("folderPath", folder.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(firstId.intValue()))
        .andExpect(jsonPath("$[0].allowedRootId").value(root.getId().intValue()))
        .andExpect(jsonPath("$[0].sourceFolder").value(folder.toRealPath().toString()))
        .andExpect(jsonPath("$[0].relativePath").value("photo.png"))
        .andExpect(jsonPath("$[0].sha256").value(firstRecord.getSha256()));

    mockMvc.perform(post("/scan").param("folderPath", folder.toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("scanning/scan"));

    mockMvc.perform(post("/api/scan").contentType(MediaType.APPLICATION_JSON).content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted.length()").value(1));

    FileRecordEntity secondRecord =
        fileRecordRepository.findByAllowedRootAndRelativePath(root, "photo.png").orElseThrow();

    assertThat(allowedRootRepository.count()).isEqualTo(1);
    assertThat(fileRecordRepository.count()).isEqualTo(1);
    assertThat(secondRecord.getId()).isEqualTo(firstId);
    assertThat(secondRecord.getSha256()).hasSize(64);
    mockMvc.perform(get("/api/catalog/files").param("folderPath", folder.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(firstId.intValue()));
    System.out.printf(
        "Web scans: roots=%d, files=%d, file ID=%d%n",
        allowedRootRepository.count(), fileRecordRepository.count(), firstId);
  }
}
