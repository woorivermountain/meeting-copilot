package com.moida.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moida.copilot.team.application.TeamService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:meeting-trash-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","copilot.llm.enabled=false"})
@AutoConfigureMockMvc
class MeetingTrashIntegrationTest {
  @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired TeamService teams;@Autowired JdbcClient db;
  record User(UUID id,MockHttpSession session){}

  @Test void trashIsRecoverableAuthorizedAndHiddenFromActiveReads() throws Exception {
    var owner=signup();var creator=signup();var outsider=signup();
    var teamData=json.readTree(mvc.perform(post("/api/teams").session(owner.session()).with(csrf()).contentType("application/json").content("{\"name\":\"삭제 검증 팀\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    String team=teamData.path("id").asText();teams.join(creator.id(),teamData.path("inviteCode").asText());
    String meeting=id(mvc.perform(post("/api/teams/"+team+"/meetings").session(creator.session()).with(csrf()).contentType("application/json").content("{\"title\":\"복구할 회의\"}")).andExpect(status().isOk()).andReturn());

    mvc.perform(delete("/api/meetings/"+meeting).session(outsider.session()).with(csrf())).andExpect(status().isForbidden());
    mvc.perform(delete("/api/meetings/"+meeting).session(creator.session())).andExpect(status().isForbidden());
    mvc.perform(delete("/api/meetings/"+meeting).session(creator.session()).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("deleted").value(true));
    mvc.perform(get("/api/meetings/"+meeting).session(creator.session())).andExpect(status().isNotFound());
    mvc.perform(get("/api/teams/"+team+"/meetings").session(creator.session())).andExpect(status().isOk()).andExpect(jsonPath("length()").value(0));
    mvc.perform(get("/api/teams/"+team+"/meetings/trash").session(creator.session())).andExpect(status().isOk()).andExpect(jsonPath("[0].id").value(meeting)).andExpect(jsonPath("[0].deletedAt").isNotEmpty());
    mvc.perform(get("/api/teams/"+team+"/meetings/trash").session(owner.session())).andExpect(status().isOk()).andExpect(jsonPath("[0].id").value(meeting));
    mvc.perform(get("/api/teams/"+team+"/meetings/trash").session(outsider.session())).andExpect(status().isForbidden());
    mvc.perform(post("/api/meetings/"+meeting+"/restore").session(creator.session()).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("deletedAt").doesNotExist());
    mvc.perform(get("/api/meetings/"+meeting).session(creator.session())).andExpect(status().isOk()).andExpect(jsonPath("title").value("복구할 회의"));
    assertEquals(List.of("MEETING_TRASHED","MEETING_RESTORED"),db.sql("select operation from app_audit where resource_id=:id order by created_at,id").param("id",UUID.fromString(meeting)).query(String.class).list());
  }

  private User signup() throws Exception {
    var session=(MockHttpSession)mvc.perform(post("/api/auth/signup").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("email",UUID.randomUUID()+"@example.test","name","검증","password","test-only-password-2026")))).andExpect(status().isOk()).andReturn().getRequest().getSession();
    var id=UUID.fromString(json.readTree(mvc.perform(get("/api/auth/me").session(session)).andReturn().getResponse().getContentAsString()).path("id").asText());return new User(id,session);
  }
  private String id(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString()).path("id").asText();}
}
