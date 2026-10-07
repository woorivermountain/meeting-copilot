package com.moida.copilot.llm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moida.copilot.llm.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
class FallbackLlmGatewayTest {
  final LlmGateway local=mock(LlmGateway.class),external=mock(LlmGateway.class);
  @Test void healthyLocalNeverCallsExternal() throws Exception {
    when(local.complete(anyList(),anyList())).thenReturn(new ObjectMapper().readTree("{\"content\":\"local\"}"));
    assertEquals("local",new FallbackLlmGateway(local,external,true).complete(List.of(),List.of()).path("content").asText());verifyNoInteractions(external);
  }
  @Test void unavailableLocalCallsConfiguredExternalOnce() throws Exception {
    when(local.complete(anyList(),anyList())).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));when(external.enabled()).thenReturn(true);
    when(external.complete(anyList(),anyList())).thenReturn(new ObjectMapper().readTree("{\"content\":\"external answer\"}"));
    assertEquals("external",new FallbackLlmGateway(local,external,true).complete(List.of(),List.of()).path("_provider").asText());verify(external,times(1)).complete(anyList(),anyList());
  }
  @Test void noConsentNeverSendsExternally(){when(local.complete(anyList(),anyList())).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));assertThrows(ResponseStatusException.class,()->new FallbackLlmGateway(local,external,false).complete(List.of(),List.of()));verifyNoInteractions(external);}
  @Test void forbiddenNeverTriggersFallback(){when(local.complete(anyList(),anyList())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));assertThrows(ResponseStatusException.class,()->new FallbackLlmGateway(local,external,true).complete(List.of(),List.of()));verifyNoInteractions(external);}
  @Test void bothFailuresDoNotLoop(){when(local.complete(anyList(),anyList())).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));when(external.enabled()).thenReturn(true);when(external.complete(anyList(),anyList())).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));assertThrows(ResponseStatusException.class,()->new FallbackLlmGateway(local,external,true).complete(List.of(),List.of()));verify(local,times(1)).complete(anyList(),anyList());verify(external,times(1)).complete(anyList(),anyList());}
}
