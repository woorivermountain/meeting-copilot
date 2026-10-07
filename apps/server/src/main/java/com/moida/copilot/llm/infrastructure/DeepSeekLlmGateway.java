package com.moida.copilot.llm.infrastructure;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moida.copilot.llm.application.LlmGateway;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Explicit provider: fixed HTTPS origin, no redirects, retries or fallback. */
public final class DeepSeekLlmGateway implements LlmGateway {
  private static final URI ENDPOINT=URI.create("https://api.deepseek.com/chat/completions");
  private final ObjectMapper json;private final String model,key;private final HttpClient client;private final Duration timeout;
  public DeepSeekLlmGateway(ObjectMapper json,String model,String key){this(json,model,key,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());}
  DeepSeekLlmGateway(ObjectMapper json,String model,String key,HttpClient client){this(json,model,key,client,Duration.ofSeconds(50));}
  DeepSeekLlmGateway(ObjectMapper json,String model,String key,HttpClient client,Duration timeout){this.json=json;this.model=model;this.key=key;this.client=client;this.timeout=timeout;if(client.followRedirects()!=HttpClient.Redirect.NEVER)throw new IllegalArgumentException("Redirects must be disabled");}
  public boolean enabled(){return !key.isBlank()&&!model.isBlank();}
  public String model(){return model;}
  public String provider(){return "deepseek";}
  public boolean externalConsentRequired(){return true;}
  public JsonNode complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools){throw new ResponseStatusException(HttpStatus.FORBIDDEN,"외부 모델은 요청별 전송 동의가 필요합니다.");}
  public JsonNode completeApproved(List<Map<String,Object>> messages,List<Map<String,Object>> tools,boolean externalApproved){
    requireApproval(externalApproved);
    if(!enabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"DeepSeek API 키 또는 모델이 설정되지 않았습니다.");
    try{
      var body=new LinkedHashMap<String,Object>();body.put("model",model);body.put("messages",messages);body.put("max_tokens",1800);body.put("stream",false);body.put("thinking",Map.of("type","disabled"));
      if(tools.isEmpty())body.put("response_format",Map.of("type","json_object"));else{body.put("tools",tools);body.put("tool_choice","auto");}
      var request=HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(50)).header("Content-Type","application/json").header("Authorization","Bearer "+key).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
      var subscriber=new LimitedBody();
      var pending=client.sendAsync(request,info->subscriber);
      try{
        // Unlike ofInputStream, this future completes only after the entire bounded body.
        var response=pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);
        byte[] bytes=response.body();
        if(response.statusCode()!=200)throw failure();
        var root=json.readTree(bytes);var message=root.path("choices").path(0).path("message");if(!message.isObject())throw failure();
        var result=(ObjectNode)message;result.put("_provider","deepseek");result.put("_model",root.path("model").asText(model));if(root.path("usage").isObject())result.set("_usage",root.path("usage"));return result;
      }finally{subscriber.cancel();pending.cancel(true);}
    }catch(InterruptedException e){Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"DeepSeek 요청이 중단되었습니다.");}catch(ResponseStatusException e){throw e;}catch(Exception e){throw failure();}
  }
  private ResponseStatusException failure(){return new ResponseStatusException(HttpStatus.BAD_GATEWAY,"DeepSeek 응답에 실패했습니다. API 설정·이용 한도·연결 상태를 확인하세요. 다른 모델로 자동 전환하지 않습니다.");}
  /** Backpressure + strict byte cap; cancellation also handles a late subscription. */
  static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> body=new CompletableFuture<>();private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private Flow.Subscription subscription;private boolean cancelled;
    public CompletionStage<byte[]> getBody(){return body;}
    public synchronized void onSubscribe(Flow.Subscription s){if(cancelled||subscription!=null){s.cancel();return;}subscription=s;s.request(1);}
    public synchronized void onNext(List<ByteBuffer> chunks){if(cancelled)return;for(var chunk:chunks){if(chunk.remaining()>131072-bytes.size()){body.completeExceptionally(new IllegalStateException("Response too large"));cancel();return;}byte[] part=new byte[chunk.remaining()];chunk.get(part);bytes.writeBytes(part);}subscription.request(1);}
    public synchronized void onError(Throwable error){body.completeExceptionally(error);}
    public synchronized void onComplete(){if(!cancelled)body.complete(bytes.toByteArray());}
    synchronized void cancel(){cancelled=true;if(subscription!=null)subscription.cancel();body.cancel(false);}
  }
}
