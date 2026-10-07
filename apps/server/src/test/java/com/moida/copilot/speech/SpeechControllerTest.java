package com.moida.copilot.speech;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moida.copilot.team.application.TeamService;
import java.nio.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.UUID;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.HttpStatus;
import java.net.http.*;
import java.io.*;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
class SpeechControllerTest {
  private byte[] wav(){byte[] out=new byte[32044];var b=ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);b.putInt(0,0x46464952);b.putInt(8,0x45564157);b.putInt(12,0x20746d66);b.putInt(16,16);b.putShort(20,(short)1);b.putShort(22,(short)1);b.putInt(24,16000);b.putShort(34,(short)16);b.putInt(36,0x61746164);b.putInt(40,32000);return out;}
  @Test void acceptsBoundedMonoPcm(){assertDoesNotThrow(()->SpeechController.validateWav(wav()));}
  @Test void rejectsMalformedAudio(){byte[] audio=wav();audio[24]=0;assertThrows(ResponseStatusException.class,()->SpeechController.validateWav(audio));}
  @Test void rejectsOversizeAudio(){assertThrows(ResponseStatusException.class,()->SpeechController.validateWav(new byte[320045]));}
  @Test void rejectsExternalDestination(){assertThrows(IllegalArgumentException.class,()->new SpeechController(new TeamService(null),new ObjectMapper(),"https://api.example.com"));}
  private final UUID team=UUID.randomUUID(),user=UUID.randomUUID();
  private final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(user.toString(),"");
  private TeamService membership(){return new TeamService(null){@Override public void requireMember(UUID t,UUID u,boolean owner){assertEquals(team,t);assertEquals(user,u);}};}
  private MockHttpServletRequest audioRequest(boolean consent){var request=new MockHttpServletRequest();request.setContent(wav());if(consent)request.addHeader("X-Speech-External-Approved","true");return request;}
  private SpeechController azure(String key,String region,String tier){return new SpeechController(membership(),new ObjectMapper(),"http://127.0.0.1:8178","azure",key,region,tier);}
  @Test void azureStatusChecksConfigurationOnlyAndNeverReturnsKey(){
    var controller=azure("testkey12345678901234567890","koreacentral","F0");var status=(Map<?,?>)controller.status(auth,team);
    assertEquals(true,status.get("ready"));assertEquals(true,status.get("external"));assertEquals("azure",status.get("provider"));assertFalse(status.toString().contains("testkey"));assertTrue(status.get("message").toString().contains("검증하지"));
  }
  @Test void azureFailsClosedForMissingKeyPaidTierAndUnsafeRegion()throws Exception{
    for(var controller:new SpeechController[]{azure("","koreacentral","F0"),azure("testkey12345678901234567890","koreacentral","S0"),azure("testkey12345678901234567890","koreacentral",""),azure("testkey12345678901234567890","koreacentral.evil.example/","F0")}){
      assertEquals(false,((Map<?,?>)controller.status(auth,team)).get("ready"));
      var failure=assertThrows(ResponseStatusException.class,()->controller.transcribe(auth,team,UUID.randomUUID(),0,audioRequest(true)));assertEquals(503,failure.getStatusCode().value());
    }
  }
  @Test void azureRequiresExplicitConsentBeforeSending(){var controller=azure("testkey12345678901234567890","koreacentral","F0");var failure=assertThrows(ResponseStatusException.class,()->controller.transcribe(auth,team,UUID.randomUUID(),0,audioRequest(false)));assertEquals(400,failure.getStatusCode().value());}
  @Test void azureMembershipRequiredEvenForStatus(){var denied=new TeamService(null){@Override public void requireMember(UUID t,UUID u,boolean owner){throw new ResponseStatusException(HttpStatus.FORBIDDEN);}};var controller=new SpeechController(denied,new ObjectMapper(),"http://127.0.0.1:8178","azure","testkey12345678901234567890","koreacentral","F0");assertThrows(ResponseStatusException.class,()->controller.status(auth,team));assertThrows(ResponseStatusException.class,()->controller.transcribe(auth,team,UUID.randomUUID(),0,audioRequest(true)));}
  @SuppressWarnings("unchecked")
  private SpeechController mockedAzure(int code,String body,AtomicInteger sends){return new SpeechController(membership(),new ObjectMapper(),"http://127.0.0.1:8178","azure","testkey12345678901234567890","koreacentral","F0"){
    @Override HttpResponse<InputStream> sendAzure(HttpRequest request){sends.incrementAndGet();assertEquals("https://koreacentral.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=ko-KR&format=simple",request.uri().toString());assertEquals("audio/wav; codecs=audio/pcm; samplerate=16000",request.headers().firstValue("Content-Type").orElseThrow());return (HttpResponse<InputStream>)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{HttpResponse.class},(proxy,method,args)->switch(method.getName()){case "statusCode"->code;case "body"->new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));default->throw new UnsupportedOperationException(method.getName());});}
  };}
  @Test void azureReturnsFinalTextAndPreservesSequence()throws Exception{var sends=new AtomicInteger();var controller=mockedAzure(200,"{\"RecognitionStatus\":\"Success\",\"DisplayText\":\" 안녕하세요 \"}",sends);var result=(Map<?,?>)controller.transcribe(auth,team,UUID.randomUUID(),7,audioRequest(true));assertEquals("안녕하세요",result.get("text"));assertEquals(7,result.get("sequence"));assertEquals(1,sends.get());}
  @Test void azureDoesNotRetryOrFallbackOnQuotaAuthRedirectOrServerFailure(){for(int code:new int[]{429,401,403,302,500}){var sends=new AtomicInteger();var controller=mockedAzure(code,"upstream secret text",sends);var failure=assertThrows(ResponseStatusException.class,()->controller.transcribe(auth,team,UUID.randomUUID(),0,audioRequest(true)));assertEquals(code==429?429:502,failure.getStatusCode().value());assertFalse(failure.getReason().contains("upstream"));assertEquals(1,sends.get());}}
  @Test void azureParsesSilenceButRejectsMalformedSuccess()throws Exception{var controller=azure("testkey12345678901234567890","koreacentral","F0");assertEquals("",controller.azureText("{\"RecognitionStatus\":\"NoMatch\"}".getBytes()));assertThrows(ResponseStatusException.class,()->controller.azureText("{\"RecognitionStatus\":\"Success\"}".getBytes()));assertThrows(ResponseStatusException.class,()->controller.azureText("{}".getBytes()));}
  @Test void azureRechecksMembershipBeforeSendAndBeforeReturningText(){
    var checks=new AtomicInteger();var sends=new AtomicInteger();var denied=new TeamService(null){@Override public void requireMember(UUID t,UUID u,boolean owner){if(checks.incrementAndGet()==3)throw new ResponseStatusException(HttpStatus.FORBIDDEN);}};
    var controller=new SpeechController(denied,new ObjectMapper(),"http://127.0.0.1:8178","azure","testkey12345678901234567890","koreacentral","F0"){@Override HttpResponse<InputStream> sendAzure(HttpRequest request)throws IOException,InterruptedException{return mockedAzure(200,"{\"RecognitionStatus\":\"Success\",\"DisplayText\":\"private\"}",sends).sendAzure(request);}};
    var failure=assertThrows(ResponseStatusException.class,()->controller.transcribe(auth,team,UUID.randomUUID(),0,audioRequest(true)));assertEquals(403,failure.getStatusCode().value());assertEquals(3,checks.get());assertEquals(1,sends.get());
  }
  @Test void azureNetworkFailureIsSanitizedAndNotRetried(){var sends=new AtomicInteger();var controller=new SpeechController(membership(),new ObjectMapper(),"http://127.0.0.1:8178","azure","testkey12345678901234567890","koreacentral","F0"){@Override HttpResponse<InputStream> sendAzure(HttpRequest request)throws IOException{sends.incrementAndGet();throw new java.net.http.HttpTimeoutException("private diagnostic");}};var failure=assertThrows(ResponseStatusException.class,()->controller.transcribe(auth,team,UUID.randomUUID(),0,audioRequest(true)));assertEquals(502,failure.getStatusCode().value());assertFalse(failure.getReason().contains("private"));assertEquals(1,sends.get());}
  @Test void closesAStalledResponseBodyAtTheTotalDeadline()throws Exception{
    var closed=new CountDownLatch(1);var input=new InputStream(){private volatile boolean done;@Override public int read()throws IOException{try{while(!done)Thread.sleep(10);return -1;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}@Override public void close(){done=true;closed.countDown();}};
    assertTimeoutPreemptively(Duration.ofSeconds(1),()->assertThrows(java.net.http.HttpTimeoutException.class,()->SpeechController.readResponse(input,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(40))));
    assertTrue(closed.await(1,TimeUnit.SECONDS));
  }
  @Test void blocksTranscriptWhenMembershipIsRevokedDuringInference()throws Exception{
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/inference",exchange->{exchange.getRequestBody().readAllBytes();byte[] response="{\"text\":\"private result\"}".getBytes();exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();});server.start();
    try{
      var team=UUID.randomUUID();var user=UUID.randomUUID();var checks=new java.util.concurrent.atomic.AtomicInteger();
      var teams=new TeamService(null){@Override public void requireMember(UUID actualTeam,UUID actualUser,boolean owner){assertEquals(team,actualTeam);assertEquals(user,actualUser);if(checks.incrementAndGet()==2)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"revoked");}};
      var controller=new SpeechController(teams,new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort());var request=new MockHttpServletRequest();request.setContent(wav());
      var failure=assertThrows(ResponseStatusException.class,()->controller.transcribe(new UsernamePasswordAuthenticationToken(user.toString(),""),team,UUID.randomUUID(),0,request));
      assertEquals(403,failure.getStatusCode().value());assertEquals(2,checks.get());
    }finally{server.stop(0);}
  }
}
