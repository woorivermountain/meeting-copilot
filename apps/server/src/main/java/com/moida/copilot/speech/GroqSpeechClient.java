package com.moida.copilot.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** One bounded request to a fixed provider. Never retries, redirects, logs audio, or falls back. */
final class GroqSpeechClient {
  private static final URI ENDPOINT=URI.create("https://api.groq.com/openai/v1/audio/transcriptions");
  private final ObjectMapper json;
  private final String key,model;
  private final Transport transport;
  private final Duration timeout;
  @FunctionalInterface interface Transport { HttpResponse<InputStream> send(HttpRequest request)throws IOException,InterruptedException; }
  GroqSpeechClient(ObjectMapper json,String key,String model){
    this(json,key,model,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build()::send,Duration.ofSeconds(40));
  }
  private GroqSpeechClient(ObjectMapper json,String key,String model,Sender sender,Duration timeout){this(json,key,model,request->sender.send(request,HttpResponse.BodyHandlers.ofInputStream()),timeout);}
  @FunctionalInterface private interface Sender { HttpResponse<InputStream> send(HttpRequest request,HttpResponse.BodyHandler<InputStream> handler)throws IOException,InterruptedException; }
  GroqSpeechClient(ObjectMapper json,String key,String model,Transport transport,Duration timeout){this.json=json;this.key=key;this.model=model;this.transport=transport;this.timeout=timeout;}
  boolean configured(){return key.matches("[A-Za-z0-9_-]{16,256}")&&Set.of("whisper-large-v3-turbo","whisper-large-v3").contains(model);}
  String transcribe(byte[] audio)throws IOException,InterruptedException{return transcribe(audio,"");}
  String transcribe(byte[] audio,String prompt)throws IOException,InterruptedException{
    if(!configured())throw failure(HttpStatus.SERVICE_UNAVAILABLE,"Groq API 키와 Whisper 모델 설정을 확인해 주세요. 다른 서비스로 전환하지 않았어요.");
    long deadline=System.nanoTime()+timeout.toNanos();
    var result=transport.send(request(audio,prompt));
    try(var input=result.body()){
      if(result.statusCode()!=200)throw upstreamFailure(result.statusCode());
      byte[] response=readBounded(input,deadline);
      if(response.length>65536)throw failure(HttpStatus.BAD_GATEWAY,"Groq 전사 응답이 너무 커요. 전사를 중단했어요.");
      var body=json.readTree(response);
      if(body==null||!body.path("text").isTextual())throw failure(HttpStatus.BAD_GATEWAY,"Groq가 유효한 전사 결과를 반환하지 않았어요. 다시 시작해 주세요.");
      return body.path("text").asText().trim();
    }
  }
  HttpRequest request(byte[] audio)throws IOException{return request(audio,"");}
  HttpRequest request(byte[] audio,String prompt)throws IOException{
    String boundary="moida-"+UUID.randomUUID();var bytes=new ByteArrayOutputStream();
    field(bytes,boundary,"model",model);field(bytes,boundary,"language","ko");field(bytes,boundary,"response_format","json");
    String bounded=Objects.toString(prompt,"").replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]","").trim();if(!bounded.isBlank())field(bytes,boundary,"prompt",bounded.substring(0,Math.min(360,bounded.length())));
    bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"chunk.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    bytes.write(audio);bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
    return HttpRequest.newBuilder(ENDPOINT).timeout(timeout).header("Authorization","Bearer "+key).header("Accept","application/json").header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build();
  }
  private static byte[] readBounded(InputStream input,long deadline)throws IOException,InterruptedException{
    var reading=CompletableFuture.supplyAsync(()->{try{return input.readNBytes(65537);}catch(IOException e){throw new CompletionException(e);}});
    try{return reading.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);}
    catch(TimeoutException e){throw new HttpTimeoutException("Speech response timed out");}
    catch(ExecutionException e){throw new IOException("Speech response failed");}
    finally{if(!reading.isDone()){reading.cancel(true);input.close();}}
  }
  private static ResponseStatusException upstreamFailure(int status){return failure(status==429?HttpStatus.TOO_MANY_REQUESTS:HttpStatus.BAD_GATEWAY,status==429?"Groq 사용 한도 또는 요청 제한에 도달해 전사를 중단했어요. 사용량을 확인한 뒤 다시 시작해 주세요. 자동 재시도나 다른 서비스 전환은 하지 않았어요.":status==401||status==403?"Groq 인증에 실패했어요. 서버의 API 키와 접근 권한을 확인해 주세요.":"Groq가 전사를 처리하지 못했어요. 설정과 서비스 상태를 확인해 주세요.");}
  private static void field(OutputStream out,String boundary,String name,String value)throws IOException{out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n").getBytes(StandardCharsets.UTF_8));}
  private static ResponseStatusException failure(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
