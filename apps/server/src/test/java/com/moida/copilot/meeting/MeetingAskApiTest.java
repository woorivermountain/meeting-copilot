package com.moida.copilot.meeting;
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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:ask-api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","copilot.llm.enabled=false"})
@AutoConfigureMockMvc
class MeetingAskApiTest {
  @Autowired MockMvc mvc;@Autowired ObjectMapper json;@MockitoBean LlmGateway llm;
  MockHttpSession signup(String email)throws Exception{return (MockHttpSession)mvc.perform(post("/api/auth/signup").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("email",email,"name","합성 사용자","password","synthetic-test-password")))).andExpect(status().isOk()).andReturn().getRequest().getSession();}
  String id(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString()).path("id").asText();}
  @Test void authenticationConsentOwnershipAndNoPersistence()throws Exception{
    mvc.perform(get("/api/llm/status")).andExpect(status().isUnauthorized());
    var owner=signup("ask-owner@example.test");var outsider=signup("ask-outsider@example.test");
    mvc.perform(get("/api/llm/status").session(owner)).andExpect(status().isOk()).andExpect(jsonPath("localConfigured").value(false)).andExpect(jsonPath("meetingExternalSupported").value(false)).andExpect(jsonPath("generationVerified").value(false));
    String team=id(mvc.perform(post("/api/teams").session(owner).with(csrf()).contentType("application/json").content("{\"name\":\"질문 테스트\"}")).andReturn());
    String meeting=id(mvc.perform(post("/api/teams/"+team+"/meetings").session(owner).with(csrf()).contentType("application/json").content("{\"title\":\"합성 회의\"}")).andReturn());
    String endpoint="/api/meetings/"+meeting+"/ask",source=UUID.randomUUID().toString();
    var payload=new HashMap<String,Object>(Map.of("question","출시 언제?","approved",true,"segments",List.of(Map.of("id",source,"receivedAt","2026-10-07T01:00:00Z","text","출시는 금요일입니다."))));
    mvc.perform(post(endpoint+"/context").session(owner).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("segments",payload.get("segments"))))).andExpect(status().isOk()).andExpect(jsonPath("brief.kind").value("DETERMINISTIC_MEETING_BRIEF")).andExpect(jsonPath("llmTokensUsed").value(0));
    mvc.perform(post(endpoint+"/context").session(outsider).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("segments",payload.get("segments"))))).andExpect(status().isForbidden());
    when(llm.completeApproved(anyList(),anyList(),anyBoolean())).thenReturn(json.createObjectNode().put("content",json.writeValueAsString(Map.of("answer","금요일입니다.","sourceIds",List.of(source)))));
    mvc.perform(post(endpoint).session(owner).contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isForbidden());
    mvc.perform(post(endpoint).session(outsider).with(csrf()).contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isForbidden());
    payload.put("approved",false);mvc.perform(post(endpoint).session(owner).with(csrf()).contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());payload.put("approved",true);
    when(llm.externalConsentRequired()).thenReturn(true);doCallRealMethod().when(llm).requireApproval(anyBoolean());
    mvc.perform(post(endpoint).session(owner).with(csrf()).contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isForbidden());
    verify(llm,never()).completeApproved(anyList(),anyList(),anyBoolean());payload.put("externalApproved",true);
    String job=id(mvc.perform(post(endpoint).session(owner).with(csrf()).contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isAccepted()).andReturn());
    mvc.perform(get(endpoint+"/"+job).session(outsider)).andExpect(status().isForbidden());
    mvc.perform(delete(endpoint+"/"+job).session(owner)).andExpect(status().isForbidden());
    mvc.perform(get("/api/meetings/"+meeting+"/transcript").session(owner)).andExpect(status().isOk()).andExpect(jsonPath("version").value(0)).andExpect(jsonPath("segments.length()").value(0));
    mvc.perform(delete(endpoint+"/"+job).session(owner).with(csrf())).andExpect(status().isOk());verify(llm,never()).complete(anyList(),anyList());
  }
}
