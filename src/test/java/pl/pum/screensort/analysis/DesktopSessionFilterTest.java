package pl.pum.screensort.analysis;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pl.pum.screensort.desktop.DesktopSessionFilter;

class DesktopSessionFilterTest {
  @Test void rejectsUntrustedLoopbackRequestsBeforeControllers() throws Exception {
    var filter = new DesktopSessionFilter("a".repeat(64));
    var response = new MockHttpServletResponse();
    filter.doFilter(new MockHttpServletRequest(), response, (request, reply) -> {
      throw new AssertionError("Unauthorized request reached controller");
    });
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
  }

  @Test void acceptsOnlyTheCurrentSessionToken() throws Exception {
    var filter = new DesktopSessionFilter("a".repeat(64));
    var request = new MockHttpServletRequest();
    request.addHeader("X-ScreenSort-Session", "a".repeat(64));
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, (req, res) -> res.getWriter().write("accepted"));
    assertThat(response.getContentAsString()).isEqualTo("accepted");
  }
}
