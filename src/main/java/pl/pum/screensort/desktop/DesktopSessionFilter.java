package pl.pum.screensort.desktop;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Only the owned Rust launcher knows the per-launch desktop API token. */
@Component
@Profile("desktop")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DesktopSessionFilter extends OncePerRequestFilter {
  private final byte[] token;

  public DesktopSessionFilter(@Value("${screensort.desktop.token}") String token) {
    if (!token.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid desktop session configuration");
    this.token = token.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    response.setHeader("Cache-Control", "no-store");
    String supplied = request.getHeader("X-ScreenSort-Session");
    if (supplied == null || !MessageDigest.isEqual(token, supplied.getBytes(StandardCharsets.UTF_8))) {
      response.setStatus(401);
      response.setContentType("application/json");
      response.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
