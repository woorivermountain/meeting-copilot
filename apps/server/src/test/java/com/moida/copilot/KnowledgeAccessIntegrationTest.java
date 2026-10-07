package com.moida.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moida.copilot.knowledge.application.*;
import com.moida.copilot.knowledge.domain.Knowledge.*;
import com.moida.copilot.team.application.TeamService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:knowledge-access-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class KnowledgeAccessIntegrationTest {
  @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired KnowledgeService knowledge; @Autowired TeamService teams;
  record User(UUID id,MockHttpSession session){}
  User signup() throws Exception {
    var session=(MockHttpSession)mvc.perform(post("/api/auth/signup").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("email",UUID.randomUUID()+"@example.test","name","검증","password","test-only-password-2026")))).andExpect(status().isOk()).andReturn().getRequest().getSession();
    return new User(UUID.fromString(json.readTree(mvc.perform(get("/api/auth/me").session(session)).andReturn().getResponse().getContentAsString()).path("id").asText()),session);
  }
  @Test void requestsRemainPrivateUntilOwnerExplicitlySharesAndEveryRetrievalRechecks() throws Exception {
    var owner=signup();var member=signup();var outsider=signup();
    var teamData=json.readTree(mvc.perform(post("/api/teams").session(owner.session()).with(csrf()).contentType("application/json").content("{\"name\":\"권한 팀\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    UUID team=UUID.fromString(teamData.path("id").asText());teams.join(member.id(),teamData.path("inviteCode").asText());
    UUID box=knowledge.box(team,owner.id(),"제품 자료","제품"),doc=knowledge.document(team,owner.id(),box,"비공개 일정","공개 전 출시 기밀",true),agent=knowledge.agent(team,owner.id(),"제품 에이전트","제품",List.of(box));
    var workspace=json.valueToTree(knowledge.workspace(team,member.id()));
    assertEquals(1,workspace.path("catalog").size());assertEquals(0,workspace.path("documents").size());assertFalse(workspace.toString().contains("공개 전 출시 기밀"));
    assertThrows(ResponseStatusException.class,()->knowledge.workspace(team,outsider.id()));
    var otherTeamData=json.readTree(mvc.perform(post("/api/teams").session(member.session()).with(csrf()).contentType("application/json").content("{\"name\":\"다른 팀\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    UUID otherTeam=UUID.fromString(otherTeamData.path("id").asText());
    assertThrows(ResponseStatusException.class,()->knowledge.requestAccess(otherTeam,member.id(),box));
    assertThrows(ResponseStatusException.class,()->knowledge.agentDocuments(otherTeam,member.id(),agent));
    mvc.perform(put("/api/teams/"+team+"/knowledge/boxes/"+box+"/sharing").session(member.session()).with(csrf()).contentType("application/json").content("{\"sharing\":\"TEAM_SHARED\"}")).andExpect(status().isForbidden());
    assertThrows(ResponseStatusException.class,()->knowledge.requestAccess(UUID.randomUUID(),member.id(),box));
    UUID request=knowledge.requestAccess(team,member.id(),box);
    assertEquals(request,knowledge.requestAccess(team,member.id(),box));
    assertThrows(ResponseStatusException.class,()->knowledge.decideAccess(team,member.id(),request,true));
    knowledge.decideAccess(team,owner.id(),request,true);
    assertThrows(ResponseStatusException.class,()->knowledge.decideAccess(team,owner.id(),request,true));
    assertEquals(doc,knowledge.agentDocuments(team,member.id(),agent).getFirst().id());
    assertTrue(knowledge.agentMeetingDocuments(team,member.id(),agent).isEmpty());
    assertTrue(knowledge.agentMeetingDocuments(team,owner.id(),agent).isEmpty());
    assertTrue(knowledge.teamMeetingDocuments(team,member.id()).isEmpty());
    assertTrue(knowledge.teamMeetingDocuments(team,owner.id()).isEmpty());
    assertThrows(ResponseStatusException.class,()->knowledge.sharing(team,member.id(),box,Sharing.TEAM_SHARED));
    knowledge.sharing(team,owner.id(),box,Sharing.TEAM_SHARED);
    assertEquals(doc,knowledge.agentMeetingDocuments(team,member.id(),agent).getFirst().id());
    assertEquals(doc,knowledge.teamMeetingDocuments(team,member.id()).getFirst().id());
    UUID otherDepartment=knowledge.box(team,member.id(),"운영 자료","운영");
    UUID otherDoc=knowledge.document(team,member.id(),otherDepartment,"운영 출시 일정","출시 지원은 월요일",true);
    knowledge.sharing(team,member.id(),otherDepartment,Sharing.TEAM_SHARED);
    assertEquals(Set.of(doc,otherDoc),new HashSet<>(knowledge.teamMeetingDocuments(team,owner.id()).stream().map(Document::id).toList()));
    assertEquals(List.of(doc),knowledge.agentMeetingDocuments(team,member.id(),agent).stream().map(Document::id).toList());
    UUID foreignBox=knowledge.box(otherTeam,member.id(),"다른 조직","다른 조직");
    knowledge.document(otherTeam,member.id(),foreignBox,"다른 팀 일정","출시 비공개",true);
    knowledge.sharing(otherTeam,member.id(),foreignBox,Sharing.TEAM_SHARED);
    assertEquals(Set.of(doc,otherDoc),new HashSet<>(knowledge.teamMeetingDocuments(team,member.id()).stream().map(Document::id).toList()));
    assertThrows(ResponseStatusException.class,()->knowledge.teamMeetingDocuments(team,outsider.id()));
    knowledge.sharing(team,member.id(),otherDepartment,Sharing.PRIVATE);
    var sources=List.of(new Source("source",doc,"비공개 일정",0,3,"공개 전"));
    knowledge.recheckMeetingSources(team,member.id(),agent,sources);
    knowledge.recheckMeetingSources(team,member.id(),null,sources);
    knowledge.sharing(team,owner.id(),box,Sharing.PRIVATE);
    assertThrows(ResponseStatusException.class,()->knowledge.recheckMeetingSources(team,member.id(),agent,sources));
    assertThrows(ResponseStatusException.class,()->knowledge.recheckMeetingSources(team,member.id(),null,sources));
    knowledge.sharing(team,owner.id(),box,Sharing.TEAM_SHARED);
    knowledge.grant(team,owner.id(),box,member.id(),false);
    assertEquals(doc,knowledge.agentDocuments(team,member.id(),agent).getFirst().id());
    assertEquals(doc,knowledge.agentMeetingDocuments(team,member.id(),agent).getFirst().id());
    knowledge.recheckMeetingSources(team,member.id(),agent,sources);
    var sharedWorkspace=json.valueToTree(knowledge.workspace(team,member.id()));
    assertTrue(sharedWorkspace.path("catalog").get(0).path("accessible").asBoolean());
    assertEquals(2,sharedWorkspace.path("boxes").size());assertEquals(2,sharedWorkspace.path("documents").size());
    assertThrows(ResponseStatusException.class,()->knowledge.agentMeetingDocuments(team,outsider.id(),agent));
    assertThrows(ResponseStatusException.class,()->knowledge.agentMeetingDocuments(otherTeam,member.id(),agent));
    knowledge.sharing(team,owner.id(),box,Sharing.PRIVATE);
    assertTrue(knowledge.agentDocuments(team,member.id(),agent).isEmpty());
    assertThrows(ResponseStatusException.class,()->knowledge.recheckMeetingSources(team,member.id(),agent,sources));
    UUID denied=knowledge.requestAccess(team,member.id(),box);
    knowledge.decideAccess(team,owner.id(),denied,false);
    assertEquals("DENIED",knowledge.requests(team,member.id()).getFirst().status());
    assertTrue(knowledge.agentDocuments(team,member.id(),agent).isEmpty());
  }
}
