package com.moida.copilot.llm.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Provider failover only. Authentication, authorization and tool validation never run here. */
public final class FallbackLlmGateway implements LlmGateway {
  private final LlmGateway local, external;
  private final boolean externalAllowed;
  public FallbackLlmGateway(LlmGateway local, LlmGateway external, boolean externalAllowed) {
    this.local=local; this.external=external; this.externalAllowed=externalAllowed;
  }
  public boolean enabled(){return local.enabled();}
  public String model(){return local.model();}
  @Override public JsonNode completeLocal(List<Map<String,Object>> messages,List<Map<String,Object>> tools){return local.completeLocal(messages,tools);}
  public JsonNode complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools) {
    try { return local.complete(messages,tools); }
    catch (ResponseStatusException failure) {
      int status=failure.getStatusCode().value();
      if(Thread.currentThread().isInterrupted() || (status!=502 && status!=503 && status!=504 && status!=429)) throw failure;
      if(!externalAllowed || !external.enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
        "로컬 모델에 연결하지 못했습니다. Ollama 실행 상태를 확인하세요. 외부 전환은 허용되지 않았거나 API 설정이 없습니다.");
      // A single bounded fallback, never a retry loop or a permission bypass.
      JsonNode result=external.complete(messages,tools).deepCopy();
      if(result.isObject()) ((com.fasterxml.jackson.databind.node.ObjectNode)result).put("_provider","external");
      return result;
    }
  }
}
