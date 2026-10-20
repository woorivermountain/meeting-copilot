package com.moida.copilot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:spa-routing;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class SpaRoutingTest {
  @Autowired MockMvc mvc;

  @Test void browserEntryRoutesRemainPublicOnDirectNavigation() throws Exception {
    for (String path : new String[]{"/", "/login", "/teams", "/teams/123", "/teams/123/knowledge", "/teams/123/usage", "/meetings/123"}) {
      mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }
  }

  @Test void servingTheShellDoesNotExposeProtectedApis() throws Exception {
    mvc.perform(get("/api/teams")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/llm/status")).andExpect(status().isUnauthorized());
  }

  @Test void missingAssetsDoNotBecomeAnHtmlSuccessResponse() throws Exception {
    mvc.perform(get("/assets/not-a-real-bundle.js")).andExpect(status().isNotFound());
  }
}
