package com.moida.copilot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.moida.copilot.llm.application.LlmGateway;
import java.util.*;

final class BudgetedLlmGateway implements LlmGateway {
  private final LlmGateway delegate;
  private final ExternalRequestBudget budget;
  BudgetedLlmGateway(LlmGateway delegate, ExternalRequestBudget budget) { this.delegate=delegate; this.budget=budget; }
  public boolean enabled(){return delegate.enabled();}
  public String model(){return delegate.model();}
  public String provider(){return delegate.provider();}
  public boolean externalConsentRequired(){return delegate.externalConsentRequired();}
  public JsonNode complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools){return delegate.complete(messages,tools);}
  public JsonNode completeLocal(List<Map<String,Object>> messages,List<Map<String,Object>> tools){return delegate.completeLocal(messages,tools);}
  public JsonNode completeApproved(List<Map<String,Object>> messages,List<Map<String,Object>> tools,boolean approved){
    delegate.requireApproval(approved);
    if(delegate.enabled() && delegate.externalConsentRequired()) budget.take("llm");
    return delegate.completeApproved(messages,tools,approved);
  }
}
