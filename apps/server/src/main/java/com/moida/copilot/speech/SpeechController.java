package com.moida.copilot.speech;
import com.moida.copilot.team.application.TeamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Explicit provider selection: no fallback and no audio persistence. */
@RestController @RequestMapping("/api/teams/{team}/speech")
public class SpeechController {
  private final TeamService teams;private final ObjectMapper json;private final URI base;
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
  private final String provider,key,region,tier;
  private final GroqSpeechClient groq;
  private final Semaphore capacity=new Semaphore(2);
  public SpeechController(TeamService teams,ObjectMapper json,String url){this(teams,json,url,"local","","","");}
  public SpeechController(TeamService teams,ObjectMapper json,String url,String provider,String key,String region,String tier){this(teams,json,url,provider,key,region,tier,"","whisper-large-v3-turbo");}
  @Autowired public SpeechController(TeamService teams,ObjectMapper json,@Value("${STT_LOCAL_URL:http://127.0.0.1:8178}") String url,@Value("${STT_PROVIDER:local}") String provider,@Value("${AZURE_SPEECH_KEY:}") String key,@Value("${AZURE_SPEECH_REGION:}") String region,@Value("${AZURE_SPEECH_TIER:}") String tier,@Value("${GROQ_API_KEY:}") String groqKey,@Value("${GROQ_STT_MODEL:whisper-large-v3-turbo}") String groqModel){
    this(teams,json,url,provider,key,region,tier,new GroqSpeechClient(json,groqKey,groqModel));
  }
  SpeechController(TeamService teams,ObjectMapper json,String url,String provider,String key,String region,String tier,GroqSpeechClient groq){
    this.teams=teams;this.json=json;this.base=URI.create(url.replaceAll("/+$",""));
    this.provider=provider;this.key=key;this.region=region;this.tier=tier;
    this.groq=groq;
    if(!Set.of("local","azure","groq").contains(provider))throw new IllegalArgumentException("STT_PROVIDER must be local, azure or groq");
    if(!"http".equals(base.getScheme())||!Set.of("127.0.0.1","[::1]").contains(base.getHost())||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null)throw new IllegalArgumentException("STT_LOCAL_URL must be a loopback HTTP URL");
  }
  @GetMapping("/status") public Object status(Authentication auth,@PathVariable UUID team){
    teams.requireMember(team,UUID.fromString(auth.getName()),false);boolean ready=false;
    if(provider.equals("groq"))return Map.of("ready",groq.configured(),"external",true,"provider","groq","message",groq.configured()?"Groq Whisper 설정이 준비됐어요. 실제 연결·전사·사용 한도와 과금 여부는 아직 검증하지 않았어요.":"Groq API 키와 Whisper 모델 설정이 필요해요. 계정의 사용 한도와 과금 설정을 확인해 주세요.");
    if(provider.equals("azure"))return Map.of("ready",azureConfigured(),"external",true,"provider","azure","message",azureConfigured()?"Azure Speech 설정이 준비됐어요. 실제 연결과 전사, F0 리소스 여부는 아직 검증하지 않았어요.":"Azure Speech 키·지역·F0 설정이 필요해요. 실제 리소스도 무료 F0인지 확인해 주세요.");
    try{var result=http.send(HttpRequest.newBuilder(URI.create(base+"/health")).timeout(Duration.ofSeconds(3)).GET().build(),HttpResponse.BodyHandlers.discarding());ready=result.statusCode()==200;}catch(Exception ignored){}
    return Map.of("ready",ready,"external",false,"provider","local","message",ready?"로컬 전사 서버가 연결됐어요.":"로컬 전사 서버가 실행되지 않았어요. 전사 API 연결 또는 로컬 서버 설정이 필요해요.");
  }
  @PostMapping(value="/transcribe",consumes="audio/wav") public Object transcribe(Authentication auth,@PathVariable UUID team,@RequestHeader("X-Speech-Session") UUID session,@RequestHeader("X-Speech-Sequence") int sequence,HttpServletRequest request) throws IOException {
    teams.requireMember(team,UUID.fromString(auth.getName()),false);
    if(provider.equals("groq")){
      if(!"true".equals(request.getHeader("X-Speech-External-Approved"))||!"groq".equals(request.getHeader("X-Speech-Provider")))throw error(HttpStatus.BAD_REQUEST,"Groq로 음성을 보내는 데 동의한 뒤 다시 시작해 주세요.");
      if(!groq.configured())throw error(HttpStatus.SERVICE_UNAVAILABLE,"Groq API 키와 Whisper 모델 설정을 확인해 주세요. 다른 전사 서비스로 전환하지 않았어요.");
    }
    if(provider.equals("azure")){
      if(!"true".equals(request.getHeader("X-Speech-External-Approved"))||(request.getHeader("X-Speech-Provider")!=null&&!"azure".equals(request.getHeader("X-Speech-Provider"))))throw error(HttpStatus.BAD_REQUEST,"Azure로 음성을 보내는 데 동의한 뒤 다시 시작해 주세요.");
      if(!azureConfigured())throw error(HttpStatus.SERVICE_UNAVAILABLE,"Azure Speech 키·지역·F0 설정을 확인해 주세요. 다른 전사 서비스로 전환하지 않았어요.");
    }
    if(sequence<0||sequence>10000)throw error(HttpStatus.BAD_REQUEST,"전사 순서를 확인해 주세요.");
    byte[] audio=request.getInputStream().readNBytes(320045);validateWav(audio);
    if(!capacity.tryAcquire())throw error(HttpStatus.TOO_MANY_REQUESTS,"전사 서버가 사용 중이에요. 잠시 후 다시 시작해 주세요.");
    long deadline=System.nanoTime()+Duration.ofSeconds(40).toNanos();
    try{
      if(provider.equals("groq")){
        teams.requireMember(team,UUID.fromString(auth.getName()),false);
        String text=groq.transcribe(audio,speechContext(request));teams.requireMember(team,UUID.fromString(auth.getName()),false);
        return Map.of("text",text.substring(0,Math.min(4000,text.length())),"sequence",sequence);
      }
      if(provider.equals("azure")){
        teams.requireMember(team,UUID.fromString(auth.getName()),false);
        var result=sendAzure(azureRequest(audio));
        try(var input=result.body()){
          if(result.statusCode()!=200)throw azureFailure(result.statusCode());
          byte[] response=readResponse(input,deadline);if(response.length>65536)throw error(HttpStatus.BAD_GATEWAY,"전사 응답이 너무 커요.");
          String text=azureText(response);teams.requireMember(team,UUID.fromString(auth.getName()),false);
          return Map.of("text",text.substring(0,Math.min(4000,text.length())),"sequence",sequence);
        }
      }
      String boundary="moida-"+UUID.randomUUID();var bytes=new ByteArrayOutputStream();
      field(bytes,boundary,"language","ko");field(bytes,boundary,"response_format","json");
      bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"chunk.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));bytes.write(audio);bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
      var result=http.send(HttpRequest.newBuilder(URI.create(base+"/inference")).timeout(Duration.ofSeconds(40)).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(),HttpResponse.BodyHandlers.ofInputStream());
      try(var input=result.body()){if(result.statusCode()!=200)throw error(HttpStatus.BAD_GATEWAY,"로컬 전사 서버가 요청을 처리하지 못했어요. 서버 설정을 확인해 주세요.");byte[] response=readResponse(input,deadline);if(response.length>65536)throw error(HttpStatus.BAD_GATEWAY,"전사 응답이 너무 커요.");String text=json.readTree(response).path("text").asText("").trim();teams.requireMember(team,UUID.fromString(auth.getName()),false);return Map.of("text",text.substring(0,Math.min(4000,text.length())),"sequence",sequence);}
    }catch(InterruptedException e){Thread.currentThread().interrupt();throw error(HttpStatus.SERVICE_UNAVAILABLE,"전사 요청이 중단됐어요.");}catch(IOException e){throw error(HttpStatus.BAD_GATEWAY,"전사 서버 연결이 끊겼거나 응답이 늦어요. 설정을 확인해 주세요.");}finally{capacity.release();}
  }
  private boolean azureConfigured(){return "F0".equals(tier)&&key.matches("[A-Za-z0-9]{16,256}")&&region.matches("[a-z][a-z0-9]{1,39}");}
  private static String speechContext(HttpServletRequest request){
    String encoded=request.getHeader("X-Speech-Context-B64");if(encoded==null||encoded.isBlank())return "";
    if(encoded.length()>4096)throw error(HttpStatus.BAD_REQUEST,"인식 보조 맥락이 너무 길어요. 용어 수를 줄여 주세요.");
    try{
      String value=new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8).trim();
      if(value.length()>360||value.indexOf('\uFFFD')>=0||value.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))throw new IllegalArgumentException();
      return value;
    }catch(IllegalArgumentException e){throw error(HttpStatus.BAD_REQUEST,"인식 보조 맥락의 형식을 확인해 주세요.");}
  }
  // HttpRequest timeout alone does not bound InputStream body reads after headers arrive.
  static byte[] readResponse(InputStream input,long deadline)throws IOException,InterruptedException{
    var reading=CompletableFuture.supplyAsync(()->{try{return input.readNBytes(65537);}catch(IOException e){throw new CompletionException(e);}});
    try{return reading.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);}
    catch(TimeoutException e){throw new HttpTimeoutException("Speech response timed out");}
    catch(ExecutionException e){throw new IOException("Speech response failed");}
    finally{if(!reading.isDone()){reading.cancel(true);input.close();}}
  }
  HttpRequest azureRequest(byte[] audio){return HttpRequest.newBuilder(URI.create("https://"+region+".stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=ko-KR&format=simple")).timeout(Duration.ofSeconds(40)).header("Ocp-Apim-Subscription-Key",key).header("Content-Type","audio/wav; codecs=audio/pcm; samplerate=16000").header("Accept","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(audio)).build();}
  HttpResponse<InputStream> sendAzure(HttpRequest request)throws IOException,InterruptedException{return http.send(request,HttpResponse.BodyHandlers.ofInputStream());}
  String azureText(byte[] response)throws IOException{var body=json.readTree(response);if(body==null)throw error(HttpStatus.BAD_GATEWAY,"Azure 전사 응답이 비어 있어요. 다시 시작해 주세요.");String status=body.path("RecognitionStatus").asText("");if(Set.of("NoMatch","InitialSilenceTimeout","BabbleTimeout").contains(status))return "";if(!status.equals("Success")||!body.path("DisplayText").isTextual())throw error(HttpStatus.BAD_GATEWAY,"Azure가 유효한 전사 결과를 반환하지 않았어요. 다시 시작해 주세요.");return body.path("DisplayText").asText().trim();}
  private static ResponseStatusException azureFailure(int status){return error(status==429?HttpStatus.TOO_MANY_REQUESTS:HttpStatus.BAD_GATEWAY,status==429?"Azure 무료 한도 또는 요청 제한에 도달했어요. Azure 사용량을 확인해 주세요. 유료 서비스로 전환하지 않았어요.":status==401||status==403?"Azure Speech 인증에 실패했어요. F0 리소스의 키와 지역을 확인해 주세요.":"Azure가 전사를 처리하지 못했어요. 설정과 서비스 상태를 확인해 주세요.");}
  static void validateWav(byte[] audio){
    if(audio.length<46||audio.length>320044)throw error(HttpStatus.PAYLOAD_TOO_LARGE,"음성 조각은 10초 이내여야 해요.");var b=ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN);
    if(b.getInt(0)!=0x46464952||b.getInt(8)!=0x45564157||b.getInt(12)!=0x20746d66||b.getInt(16)!=16||b.getShort(20)!=1||b.getShort(22)!=1||b.getInt(24)!=16000||b.getShort(34)!=16||b.getInt(36)!=0x61746164||b.getInt(40)!=audio.length-44)throw error(HttpStatus.BAD_REQUEST,"16kHz 모노 WAV 입력이 필요해요.");
  }
  private static void field(OutputStream out,String boundary,String name,String value)throws IOException{out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n").getBytes(StandardCharsets.UTF_8));}
  private static ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
