package pl.pum.screensort.analysis;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Whether new images in a folder are analysed as soon as the folder is read. Off until the owner
 * turns it on: the model uses the processor or graphics card, which they may need for other work.
 * The app reads the setting after each folder read and starts the analysis itself.
 */
@RestController
@RequestMapping("/api/settings/auto-analysis")
public class AutoAnalysisController {
  private static final String KEY = "analysis.auto";
  private final JdbcTemplate jdbc;

  public AutoAnalysisController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public record AutoAnalysis(Boolean enabled) {}

  @ModelAttribute
  public void preventCaching(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

  @GetMapping
  @Transactional(readOnly = true)
  public AutoAnalysis read() {
    var values = jdbc.queryForList("select value from app_setting where name = ?", String.class, KEY);
    return new AutoAnalysis(!values.isEmpty() && Boolean.parseBoolean(values.getFirst()));
  }

  @PutMapping
  @Transactional
  public AutoAnalysis change(@RequestBody AutoAnalysis request) {
    if (request.enabled() == null) throw new AnalysisException(400, "Podaj, czy analizować nowe zdjęcia automatycznie.");
    jdbc.update("""
        insert into app_setting(name, value) values (?, ?)
        on conflict (name) do update set value = excluded.value
        """, KEY, request.enabled().toString());
    return new AutoAnalysis(request.enabled());
  }
}
