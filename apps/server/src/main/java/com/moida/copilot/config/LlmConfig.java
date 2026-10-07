package com.moida.copilot.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moida.copilot.llm.application.*;
import com.moida.copilot.llm.infrastructure.CompatibleLlmGateway;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
public class LlmConfig {
  @Bean LlmGateway llmGateway(ObjectMapper json, Environment env) {
    String provider=env.getProperty("copilot.llm.provider","local");
    if(provider.equals("deepseek"))return new com.moida.copilot.llm.infrastructure.DeepSeekLlmGateway(json,env.getProperty("copilot.llm.deepseek.model","deepseek-flash"),env.getProperty("copilot.llm.deepseek.api-key",""));
    if(!provider.equals("local"))throw new IllegalArgumentException("LLM_PROVIDER must be local or deepseek");
    var local=new CompatibleLlmGateway(json,env.getProperty("copilot.llm.base-url"),env.getProperty("copilot.llm.model"),env.getProperty("copilot.llm.api-key",""),env.getProperty("copilot.llm.enabled",Boolean.class,true),env.getProperty("LLM_REASONING_EFFORT","none"));
    String base=env.getProperty("copilot.llm.fallback.base-url",""), model=env.getProperty("copilot.llm.fallback.model",""), key=env.getProperty("copilot.llm.fallback.api-key","");
    boolean configured=!base.isBlank()&&!model.isBlank()&&!key.isBlank();
    if(configured && !base.startsWith("https://"))throw new IllegalArgumentException("External LLM requires HTTPS");
    var external=new CompatibleLlmGateway(json,configured?base:"https://unconfigured.invalid/v1",model,key,configured);
    return new FallbackLlmGateway(local,external,env.getProperty("copilot.llm.fallback.allowed",Boolean.class,false));
  }
}
