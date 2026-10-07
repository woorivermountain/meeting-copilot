package com.moida.copilot.llm.infrastructure;
import com.moida.copilot.llm.application.LlmGateway;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public class CompatibleLlmGateway implements LlmGateway {
  private String reasoningEffort="";
  public CompatibleLlmGateway(ObjectMapper json,String base,String model,String key,boolean enabled,String reasoningEffort){this(json,base,model,key,enabled);this.reasoningEffort=reasoningEffort;}
  private final ObjectMapper json;private final URI endpoint;private final String model,key;private final boolean enabled;
  private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
  public CompatibleLlmGateway(ObjectMapper json,String base,String model,String key,boolean enabled){this.json=json;this.endpoint=URI.create(base.replaceAll("/+$","")+"/chat/completions");this.model=model;this.key=key;this.enabled=enabled;if(endpoint.getHost()==null||(!"https".equals(endpoint.getScheme())&&!("http".equals(endpoint.getScheme())&&Set.of("localhost","127.0.0.1","::1","[::1]").contains(endpoint.getHost()))))throw new IllegalArgumentException("LLM endpoint must use HTTPS or loopback HTTP");}
  public boolean enabled(){return enabled;}public String model(){return model;}
  public JsonNode complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools){
    if(!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"LLM이 연결되지 않았습니다. 서버의 LLM_ENABLED와 모델 설정을 확인하세요.");
    try{var body=new LinkedHashMap<String,Object>();body.put("model",model);body.put("messages",messages);body.put("max_tokens",1800);body.put("stream",false);if(!tools.isEmpty()){body.put("tools",tools);body.put("tool_choice","auto");}
      if(!reasoningEffort.isBlank())body.put("reasoning_effort",reasoningEffort);
      if(tools.isEmpty())body.put("response_format",Map.of("type","json_object"));
      var builder=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(50)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));if(!key.isBlank())builder.header("Authorization","Bearer "+key);
      var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());try(var stream=response.body()){byte[] bytes=stream.readNBytes(131073);if(response.statusCode()!=200||bytes.length>131072)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"모델 응답에 실패했습니다. 모델 실행·API 설정·이용 한도를 확인하세요.");var message=json.readTree(bytes).path("choices").path(0).path("message");if(message.isMissingNode())throw new IllegalStateException();return message;}
    }catch(InterruptedException e){Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"모델 요청이 중단되었습니다.");}catch(ResponseStatusException e){throw e;}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"모델에 연결하지 못했거나 응답 시간이 초과되었습니다. 원문과 회의 기록은 변경되지 않았습니다.");}
  }
}
