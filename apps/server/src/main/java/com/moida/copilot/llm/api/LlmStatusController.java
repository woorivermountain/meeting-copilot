package com.moida.copilot.llm.api;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

/** A local models-list probe is not a successful generation/tool-call claim. No secrets returned. */
@RestController @RequestMapping("/api/llm/status")
public class LlmStatusController {
  private final Environment env;private final ObjectMapper json;
  private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build();
  private Map<String,Object> cached;private Instant expires=Instant.MIN;
  public LlmStatusController(Environment env,ObjectMapper json){this.env=env;this.json=json;}
  @GetMapping public synchronized Object status(){
    if(cached!=null&&Instant.now().isBefore(expires))return cached;
    if("deepseek".equals(env.getProperty("copilot.llm.provider","local"))){
      String model=env.getProperty("copilot.llm.deepseek.model","deepseek-flash");boolean configured=!model.isBlank()&&!env.getProperty("copilot.llm.deepseek.api-key","").isBlank();
      var result=new LinkedHashMap<String,Object>();result.put("provider","deepseek");result.put("configured",configured);result.put("externalConsentRequired",true);result.put("model",model);result.put("generationVerified",false);result.put("probe","none");result.put("localConfigured",false);result.put("localReachable",false);result.put("modelAvailable",false);result.put("meetingExternalSupported",true);result.put("externalFallbackAllowed",false);result.put("externalFallbackConfigured",false);result.put("checkedAt",Instant.now());cached=Collections.unmodifiableMap(result);expires=Instant.now().plusSeconds(10);return cached;
    }
    String base=env.getProperty("copilot.llm.base-url",""),model=env.getProperty("copilot.llm.model","");boolean enabled=env.getProperty("copilot.llm.enabled",Boolean.class,true),configured=false,reachable=false,modelAvailable=false;
    try{URI uri=URI.create(base.replaceAll("/+$","")+"/models");configured=enabled&&!model.isBlank()&&Set.of("localhost","127.0.0.1","::1","[::1]").contains(uri.getHost())&&Set.of("http","https").contains(uri.getScheme());
      if(configured){var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).GET().build();var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());try(var stream=response.body()){byte[] bytes=stream.readNBytes(65537);if(response.statusCode()==200&&bytes.length<=65536){var data=json.readTree(bytes).path("data");reachable=data.isArray();for(var item:data)if(model.equals(item.path("id").asText()))modelAvailable=true;}}}
    }catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception ignored){}
    var result=new LinkedHashMap<String,Object>();result.put("provider","local");result.put("configured",configured);result.put("externalConsentRequired",false);result.put("model",model);result.put("localConfigured",configured);result.put("localReachable",reachable);result.put("modelAvailable",modelAvailable);result.put("probe","models");result.put("generationVerified",false);result.put("checkedAt",Instant.now());result.put("externalFallbackAllowed",false);result.put("externalFallbackConfigured",false);result.put("meetingExternalSupported",false);cached=Collections.unmodifiableMap(result);expires=Instant.now().plusSeconds(10);return cached;
  }
}
