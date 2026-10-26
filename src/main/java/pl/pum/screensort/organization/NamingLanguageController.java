package pl.pum.screensort.organization;

import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.pum.screensort.analysis.AnalysisException;
import pl.pum.screensort.analysis.database.AnalysisStore;
import pl.pum.screensort.analysis.result.AnalysisResult;
import pl.pum.screensort.naming.NameSuggestion;
import pl.pum.screensort.naming.NameSuggestionGenerator;
import pl.pum.screensort.naming.NamingLanguage;
import pl.pum.screensort.naming.NamingSettings;

/**
 * The language of proposed names. Changing it rewrites the proposals of finished folder items from
 * their saved analysis, so the owner sees the new names at once without running the model again.
 * Approved names are stored separately and stay as they are.
 */
@RestController
@RequestMapping("/api/settings/naming-language")
public class NamingLanguageController {
  private final NamingSettings settings;
  private final NameSuggestionGenerator names;
  private final AnalysisStore analyses;
  private final JdbcTemplate jdbc;

  public NamingLanguageController(NamingSettings settings, NameSuggestionGenerator names,
      AnalysisStore analyses, JdbcTemplate jdbc) {
    this.settings = settings; this.names = names; this.analyses = analyses; this.jdbc = jdbc;
  }

  public record Language(String language) {}

  @ModelAttribute
  public void preventCaching(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

  @GetMapping
  public Language read() { return new Language(settings.language().name()); }

  @PutMapping
  @Transactional
  public Language change(@RequestBody Language request) {
    NamingLanguage language;
    try { language = NamingLanguage.valueOf(request.language()); }
    catch (IllegalArgumentException | NullPointerException error) {
      throw new AnalysisException(400, "Wybierz język nazw: PL albo EN.");
    }
    settings.language(language);
    refreshProposals();
    return new Language(language.name());
  }

  private void refreshProposals() {
    record Item(UUID batch, long file, String originalName, long analysis) {}
    var items = jdbc.query("""
        select batch_id, file_id, original_name, analysis_id from analysis_batch_item
        where status = 'COMPLETED' and analysis_id is not null
        """, (row, n) -> new Item(row.getObject(1, UUID.class), row.getLong(2), row.getString(3),
        row.getLong(4)));
    for (var item : items) {
      var saved = analyses.find(item.analysis()).orElse(null);
      if (saved == null) continue;
      var ocr = saved.result().ocr().status() == AnalysisResult.Status.COMPLETED
          ? saved.result().ocr().result().text() : null;
      var visual = saved.result().visual().status() == AnalysisResult.Status.COMPLETED
          ? saved.result().visual().result().description() : null;
      var proposal = names.suggest(item.originalName(), ocr, visual);
      jdbc.update("""
          update analysis_batch_item set proposed_name = ?, proposal_uncertain = ?
          where batch_id = ? and file_id = ?
          """, proposal.map(NameSuggestion::name).orElse(null),
          proposal.map(NameSuggestion::uncertain).orElse(false), item.batch(), item.file());
    }
  }
}
