package com.moida.copilot.llm.application;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
public interface LlmGateway {
  JsonNode complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools);
  boolean enabled();
  String model();
}
