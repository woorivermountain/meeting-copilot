package com.moida.copilot;
import com.fasterxml.jackson.databind.*;
import com.moida.copilot.llm.application.LlmGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockHttpSession;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:service-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class ServiceIntegrationTest {
  @Autowired MockMvc mvc;@Autowired ObjectMapper json;@MockitoBean LlmGateway llm;
  @Autowired com.moida.copilot.knowledge.application.KnowledgeService knowledge;
  @Autowired com.moida.copilot.knowledge.application.AgentRunner runner;
  @Autowired com.moida.copilot.team.application.TeamService teamService;
  MockHttpSession signup(String email) throws Exception {
    return (MockHttpSession)mvc.perform(post("/api/auth/signup").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("email",email,"name","검증 사용자","password","test-only-password-2026")))).andExpect(status().isOk()).andReturn().getRequest().getSession();
  }
  String id(MvcResult result) throws Exception{return json.readTree(result.getResponse().getContentAsString()).path("id").asText();}
  UUID user(MockHttpSession session) throws Exception{return UUID.fromString(id(mvc.perform(get("/api/auth/me").session(session)).andReturn()));}
  @Test void toolsHonorUserAndAgentIntersectionAndRevocation() throws Exception {
    var owner=signup("knowledge-owner@example.test");var member=signup("knowledge-member@example.test");UUID ownerId=user(owner),memberId=user(member);
    var created=json.readTree(mvc.perform(post("/api/teams").session(owner).with(csrf()).contentType("application/json").content("{\"name\":\"자료 팀\"}")).andReturn().getResponse().getContentAsString());UUID team=UUID.fromString(created.path("id").asText());teamService.join(memberId,created.path("inviteCode").asText());
    UUID box=knowledge.box(team,ownerId,"개발","제품"),doc=knowledge.document(team,ownerId,box,"일정","출시는 금요일입니다.",true),agent=knowledge.agent(team,ownerId,"개발 담당","제품",List.of(box));
    org.junit.jupiter.api.Assertions.assertTrue(knowledge.agentDocuments(team,memberId,agent).isEmpty());
    knowledge.grant(team,ownerId,box,memberId,true);org.junit.jupiter.api.Assertions.assertEquals(doc,knowledge.agentDocuments(team,memberId,agent).getFirst().id());
    var call=json.readTree("{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"search_documents\",\"arguments\":\"{\\\"query\\\":\\\"출시\\\"}\"}}]}");
    when(llm.complete(anyList(),anyList())).thenAnswer(invocation->{List<Map<String,Object>> messages=invocation.getArgument(0);if(messages.size()==2)return call;var payload=json.readTree((String)messages.getLast().get("content"));String sid=payload.path("sources").path(0).path("sourceId").asText();return json.createObjectNode().put("content",json.writeValueAsString(Map.of("answer","금요일 출시입니다.","sourceIds",List.of(sid))));});
    var result=json.valueToTree(runner.ask(team,memberId,agent,"출시 일정"));org.junit.jupiter.api.Assertions.assertEquals("DRAFT",result.path("status").asText());org.junit.jupiter.api.Assertions.assertEquals("출시는 금요일입니다.",result.path("citations").path(0).path("quote").asText());
    knowledge.grant(team,ownerId,box,memberId,false);org.junit.jupiter.api.Assertions.assertTrue(knowledge.agentDocuments(team,memberId,agent).isEmpty());
    org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,()->knowledge.grant(team,memberId,box,memberId,true));
  }
  @Test void realSessionApprovalRevisionAndIsolation() throws Exception {
    var owner=signup("owner@example.test");var outsider=signup("outsider@example.test");
    mvc.perform(get("/api/auth/me").session(owner)).andExpect(status().isOk()).andExpect(jsonPath("email").value("owner@example.test"));
    mvc.perform(post("/api/teams").session(owner).contentType("application/json").content("{\"name\":\"차단\"}")).andExpect(status().isForbidden());
    String team=id(mvc.perform(post("/api/teams").session(owner).with(csrf()).contentType("application/json").content("{\"name\":\"테스트 팀\"}")).andExpect(status().isOk()).andReturn());
    String meeting=id(mvc.perform(post("/api/teams/"+team+"/meetings").session(owner).with(csrf()).contentType("application/json").content("{\"title\":\"검증 회의\"}")).andExpect(status().isOk()).andReturn());
    mvc.perform(get("/api/meetings/"+meeting).session(outsider)).andExpect(status().isForbidden());
    String source=UUID.randomUUID().toString();
    var payload=new HashMap<String,Object>(Map.of("version",0,"approved",false,"segments",List.of(Map.of("id",source,"receivedAt","2026-10-07T01:00:00Z","text","김민수가 금요일까지 초안을 작성한다."))));
    mvc.perform(put("/api/meetings/"+meeting+"/transcript").session(owner).with(csrf()).contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());
    payload.put("approved",true);
    mvc.perform(put("/api/meetings/"+meeting+"/transcript").session(owner).with(csrf()).contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isOk()).andExpect(jsonPath("version").value(1));
    mvc.perform(put("/api/meetings/"+meeting+"/transcript").session(owner).with(csrf()).contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isConflict());
    mvc.perform(get("/api/meetings/"+meeting+"/transcript/history").session(owner)).andExpect(status().isOk()).andExpect(jsonPath("length()").value(1));
    var answer=Map.of("items",List.of(Map.of("kind","ACTION","text","김민수 초안 작성","sourceIds",List.of(source))));
    when(llm.complete(anyList(),anyList())).thenReturn(json.createObjectNode().put("content",json.writeValueAsString(answer)));
    mvc.perform(post("/api/meetings/"+meeting+"/summary").session(owner).with(csrf()).contentType("application/json").content("{\"version\":1,\"approved\":true}")).andExpect(status().isOk()).andExpect(jsonPath("items[0].sources[0].id").value(source));
    when(llm.complete(anyList(),anyList())).thenReturn(json.createObjectNode().put("content","{\"items\":[{\"kind\":\"ACTION\",\"text\":\"허위\",\"sourceIds\":[\"missing\"]}]}"));
    mvc.perform(post("/api/meetings/"+meeting+"/summary").session(owner).with(csrf()).contentType("application/json").content("{\"version\":1,\"approved\":true}")).andExpect(status().isBadGateway());
    mvc.perform(get("/api/meetings/"+meeting+"/transcript").session(owner)).andExpect(status().isOk()).andExpect(jsonPath("version").value(1));
  }
}
