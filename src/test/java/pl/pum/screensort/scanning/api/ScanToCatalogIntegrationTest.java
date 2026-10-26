package pl.pum.screensort.scanning.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import pl.pum.screensort.catalog.database.AllowedRootEntity;
import pl.pum.screensort.catalog.database.AllowedRootRepository;
import pl.pum.screensort.catalog.database.FileRecordEntity;
import pl.pum.screensort.catalog.database.FileRecordRepository;

/** A real scan job from the HTTP API to the catalog, previews and a rescan with stable IDs. */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class ScanToCatalogIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));

  @Autowired private MockMvc mockMvc;
  @Autowired private AllowedRootRepository allowedRootRepository;
  @Autowired private FileRecordRepository fileRecordRepository;

  @Test
  void scanJobsReportIncrementalSummaryAndKeepStableFileIds(@TempDir Path folder)
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

    scan(requestJson)
        .andExpect(jsonPath("$.summary.newImages").value(1))
        .andExpect(jsonPath("$.summary.rescannedImages").value(0))
        .andExpect(jsonPath("$.summary.unchangedImages").value(0))
        .andExpect(jsonPath("$.summary.rejectedFiles").value(0));

    AllowedRootEntity root =
        allowedRootRepository.findByCanonicalPath(folder.toRealPath().toString()).orElseThrow();
    FileRecordEntity firstRecord =
        fileRecordRepository.findByAllowedRootAndRelativePath(root, "photo.png").orElseThrow();
    Long firstId = firstRecord.getId();

    // Prawdziwy przepływ: rekord JPA -> bezpieczny plik -> PNG, bez nowego skanu.
    byte[] originalBytes = Files.readAllBytes(imagePath);
    mockMvc.perform(get("/api/catalog/files/" + firstId + "/thumbnail"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(header().string("Cache-Control", "no-store"));
    mockMvc.perform(get("/api/catalog/files/" + firstId + "/preview"))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/catalog/files/999999/thumbnail"))
        .andExpect(status().isNotFound());
    assertThat(Files.readAllBytes(imagePath)).isEqualTo(originalBytes);

    mockMvc.perform(get("/api/catalog/files").param("folderPath", folder.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(firstId.intValue()))
        .andExpect(jsonPath("$[0].allowedRootId").value(root.getId().intValue()))
        .andExpect(jsonPath("$[0].sourceFolder").value(folder.toRealPath().toString()))
        .andExpect(jsonPath("$[0].relativePath").value("photo.png"))
        .andExpect(jsonPath("$[0].sha256").value(firstRecord.getSha256()));

    scan(requestJson)
        .andExpect(jsonPath("$.summary.newImages").value(0))
        .andExpect(jsonPath("$.summary.rescannedImages").value(0))
        .andExpect(jsonPath("$.summary.unchangedImages").value(1))
        .andExpect(jsonPath("$.summary.rejectedFiles").value(0));

    FileRecordEntity secondRecord =
        fileRecordRepository.findByAllowedRootAndRelativePath(root, "photo.png").orElseThrow();

    assertThat(allowedRootRepository.count()).isEqualTo(1);
    assertThat(fileRecordRepository.count()).isEqualTo(1);
    assertThat(secondRecord.getId()).isEqualTo(firstId);
    assertThat(secondRecord.getSha256()).hasSize(64);
    assertThat(secondRecord.getSha256()).isEqualTo(firstRecord.getSha256());

    // Znany obraz z inną zawartością musi być sprawdzony ponownie, bez nowego ID.
    assertThat(
            ImageIO.write(
                new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", imagePath.toFile()))
        .isTrue();
    Files.setLastModifiedTime(
        imagePath, FileTime.from(firstRecord.getLastModifiedAt().plusSeconds(120)));
    scan(requestJson)
        .andExpect(jsonPath("$.summary.newImages").value(0))
        .andExpect(jsonPath("$.summary.rescannedImages").value(1))
        .andExpect(jsonPath("$.summary.unchangedImages").value(0))
        .andExpect(jsonPath("$.summary.rejectedFiles").value(0));

    FileRecordEntity changedRecord =
        fileRecordRepository.findByAllowedRootAndRelativePath(root, "photo.png").orElseThrow();
    assertThat(changedRecord.getId()).isEqualTo(firstId);
    assertThat(changedRecord.getSha256()).isNotEqualTo(firstRecord.getSha256());
    assertThat(fileRecordRepository.count()).isEqualTo(1);

    // Jeden skan może jednocześnie zawierać nowy, znany i odrzucony plik.
    assertThat(
            ImageIO.write(
                new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB),
                "png",
                folder.resolve("new-photo.png").toFile()))
        .isTrue();
    Files.writeString(folder.resolve("notes.txt"), "Not an image.");
    String location = scanLocation(requestJson);
    mockMvc.perform(get(location))
        .andExpect(jsonPath("$.summary.newImages").value(1))
        .andExpect(jsonPath("$.summary.rescannedImages").value(0))
        .andExpect(jsonPath("$.summary.unchangedImages").value(1))
        .andExpect(jsonPath("$.summary.rejectedFiles").value(1));
    mockMvc.perform(get(location + "/rejections"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.files.length()").value(1));

    assertThat(allowedRootRepository.count()).isEqualTo(1);
    assertThat(fileRecordRepository.count()).isEqualTo(2);
    mockMvc.perform(get("/api/catalog/files").param("folderPath", folder.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  /** Starts a scan job and returns the finished job, so assertions read its summary. */
  private ResultActions scan(String requestJson) throws Exception {
    return mockMvc.perform(get(scanLocation(requestJson))).andExpect(status().isOk());
  }

  private String scanLocation(String requestJson) throws Exception {
    String location = mockMvc
        .perform(post("/api/scan/jobs").contentType(MediaType.APPLICATION_JSON).content(requestJson))
        .andExpect(status().isAccepted())
        .andReturn().getResponse().getHeader("Location");
    for (int attempt = 0; attempt < 200; attempt++) {
      String job = mockMvc.perform(get(location)).andReturn().getResponse().getContentAsString();
      String state = JsonPath.read(job, "$.status");
      if (state.equals("COMPLETED")) return location;
      if (state.equals("FAILED")) throw new AssertionError("Scan job failed");
      Thread.sleep(50);
    }
    throw new AssertionError("Scan job did not finish within 10 s");
  }
}
