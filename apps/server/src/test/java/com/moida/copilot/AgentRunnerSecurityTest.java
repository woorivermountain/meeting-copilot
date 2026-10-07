package com.moida.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moida.copilot.knowledge.application.*;
import com.moida.copilot.knowledge.domain.Knowledge.*;
import com.moida.copilot.knowledge.infrastructure.KnowledgeRepository;
import com.moida.copilot.llm.application.LlmGateway;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentRunnerSecurityTest {
  @Test void revocationDuringModelAnswerBlocksPreviouslyRetrievedContent() throws Exception {
    var knowledge=mock(KnowledgeService.class);var repo=mock(KnowledgeRepository.class);var llm=mock(LlmGateway.class);var json=new ObjectMapper();
    UUID team=UUID.randomUUID(),user=UUID.randomUUID(),agent=UUID.randomUUID(),docId=UUID.randomUUID();
    var doc=new Document(docId,UUID.randomUUID(),"일정","금요일 출시");var revoked=new AtomicBoolean();
    when(knowledge.agentDocuments(team,user,agent)).thenAnswer(i->revoked.get()?List.of():List.of(doc));
    when(llm.completeApproved(anyList(),anyList(),anyBoolean())).thenAnswer(i->{
      List<Map<String,Object>> messages=i.getArgument(0);
      if(messages.size()==2)return json.readTree("{\"tool_calls\":[{\"id\":\"one\",\"type\":\"function\",\"function\":{\"name\":\"search_documents\",\"arguments\":\"{\\\"query\\\":\\\"출시\\\"}\"}}]}");
      var payload=json.readTree((String)messages.getLast().get("content"));String source=payload.path("sources").get(0).path("sourceId").asText();revoked.set(true);
      return json.createObjectNode().put("content",json.writeValueAsString(Map.of("answer","금요일 출시","sourceIds",List.of(source))));
    });
    var error=assertThrows(ResponseStatusException.class,()->new AgentRunner(knowledge,repo,llm,json).ask(team,user,agent,"출시"));
    assertEquals(403,error.getStatusCode().value());verify(repo,never()).audit(any(),any(),any(),any());verify(llm,never()).complete(anyList(),anyList());
  }
  @Test void localFailureNeverUsesExternalFallback() {
    var knowledge=mock(KnowledgeService.class);var repo=mock(KnowledgeRepository.class);var llm=mock(LlmGateway.class);
    UUID team=UUID.randomUUID(),user=UUID.randomUUID(),agent=UUID.randomUUID();
    when(knowledge.agentDocuments(team,user,agent)).thenReturn(List.of());
    when(llm.completeApproved(anyList(),anyList(),anyBoolean())).thenThrow(new IllegalStateException("local unavailable"));
    assertThrows(ResponseStatusException.class,()->new AgentRunner(knowledge,repo,llm,new ObjectMapper()).ask(team,user,agent,"일정"));
    verify(llm,never()).complete(anyList(),anyList());
  }
}
