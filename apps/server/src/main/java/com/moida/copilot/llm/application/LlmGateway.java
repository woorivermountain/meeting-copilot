package com.moida.copilot.llm.application;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
public interface LlmGateway {
  JsonNode complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools);
  /** Fail closed unless an implementation verifies a loopback-only destination. */
  default JsonNode completeLocal(List<Map<String,Object>> messages,List<Map<String,Object>> tools){throw new IllegalStateException("Local-only model unavailable");}
  boolean enabled();
  default JsonNode completeApproved(List<Map<String,Object>> messages,List<Map<String,Object>> tools,boolean externalApproved){requireApproval(externalApproved);return completeLocal(messages,tools);}
  default String provider(){return "local";}
  default boolean externalConsentRequired(){return false;}
  default void requireApproval(boolean externalApproved){if(externalConsentRequired()&&!externalApproved)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"DeepSeek 외부 전송에 대한 이번 요청의 동의가 필요합니다.");}
  String model();
}
