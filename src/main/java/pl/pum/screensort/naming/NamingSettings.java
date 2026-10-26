package pl.pum.screensort.naming;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The owner's choice of name language; Polish until they pick something else. */
@Service
public class NamingSettings {
  private static final String KEY = "naming.language";

  private final JdbcTemplate jdbc;

  public NamingSettings(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional(readOnly = true)
  public NamingLanguage language() {
    var values = jdbc.queryForList("select value from app_setting where name = ?", String.class, KEY);
    return values.isEmpty() ? NamingLanguage.PL : NamingLanguage.valueOf(values.getFirst());
  }

  @Transactional
  public void language(NamingLanguage language) {
    jdbc.update("""
        insert into app_setting(name, value) values (?, ?)
        on conflict (name) do update set value = excluded.value
        """, KEY, language.name());
  }
}
