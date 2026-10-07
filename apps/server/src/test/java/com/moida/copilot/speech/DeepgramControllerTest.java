package com.moida.copilot.speech;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moida.copilot.team.application.TeamService;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class DeepgramControllerTest {
 private static final String KEY="syntheticMemberKey123456789",TOKEN="synthetic-temporary-token-123456";
 private final UUID team=UUID.randomUUID(),user=UUID.randomUUID();
 private final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(user.toString(),"");
 private TeamService members(){return new TeamService(null){@Override public void requireMember(UUID t,UUID u,boolean owner){assertEquals(team,t);assertEquals(user,u);}};}
 @Test void missingKeyAndConsentFailWithoutExternalRequests(){
  var c=new DeepgramController(members(),new ObjectMapper(),"");assertEquals(false,((Map<?,?>)c.status(auth,team)).get("ready"));
  assertEquals(400,assertThrows(ResponseStatusException.class,()->c.token(auth,team,new DeepgramController.Approval(false),new MockHttpSession())).getStatusCode().value());
  assertEquals(503,assertThrows(ResponseStatusException.class,()->c.token(auth,team,new DeepgramController.Approval(true),new MockHttpSession())).getStatusCode().value());
 }

 @Test void successfulGrantIsNoStoreAndRateLimitedWithoutExposingPermanentKey(){
  var sends=new AtomicInteger();var controller=controller(members(),request->{sends.incrementAndGet();assertEquals("https://api.deepgram.com/v1/auth/grant",request.uri().toString());assertEquals("Token "+KEY,request.headers().firstValue("Authorization").orElseThrow());return response(200,"{\"access_token\":\""+TOKEN+"\"}");});var session=new MockHttpSession();
  var result=controller.token(auth,team,new DeepgramController.Approval(true),session);
  assertEquals(TOKEN,((Map<?,?>)result.getBody()).get("accessToken"));assertTrue(Objects.requireNonNull(result.getHeaders().getCacheControl()).contains("no-store"));
  assertEquals(429,assertThrows(ResponseStatusException.class,()->controller.token(auth,team,new DeepgramController.Approval(true),session)).getStatusCode().value());assertEquals(1,sends.get());
 }

 @Test void failedProviderGrantDoesNotConsumeRetryWindow(){
  var sends=new AtomicInteger();var controller=controller(members(),request->sends.incrementAndGet()==1?response(500,"{}"):response(200,"{\"access_token\":\""+TOKEN+"\"}"));var session=new MockHttpSession();
  assertEquals(502,assertThrows(ResponseStatusException.class,()->controller.token(auth,team,new DeepgramController.Approval(true),session)).getStatusCode().value());
  assertEquals(TOKEN,((Map<?,?>)controller.token(auth,team,new DeepgramController.Approval(true),session).getBody()).get("accessToken"));assertEquals(2,sends.get());
 }

 @Test void membershipRevocationAfterGrantPreventsTokenDisclosure(){
  var checks=new AtomicInteger();var revoked=new TeamService(null){@Override public void requireMember(UUID t,UUID u,boolean owner){if(checks.incrementAndGet()>1)throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);}};var session=new MockHttpSession();var controller=controller(revoked,request->response(200,"{\"access_token\":\""+TOKEN+"\"}"));
  assertEquals(403,assertThrows(ResponseStatusException.class,()->controller.token(auth,team,new DeepgramController.Approval(true),session)).getStatusCode().value());assertNull(session.getAttribute("deepgramGrantAt"));assertNull(session.getAttribute("deepgramGrantPending"));
 }

 @Test void stalledBodyTimesOutClosesInputAndClearsPendingReservation(){
  var closed=new CountDownLatch(1);var stalled=new InputStream(){@Override public int read()throws IOException{try{closed.await();return -1;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException();}}@Override public void close(){closed.countDown();}};var session=new MockHttpSession();var controller=new DeepgramController(members(),new ObjectMapper(),KEY,request->response(200,stalled),Duration.ofMillis(30));
  assertTimeoutPreemptively(Duration.ofSeconds(2),()->assertEquals(502,assertThrows(ResponseStatusException.class,()->controller.token(auth,team,new DeepgramController.Approval(true),session)).getStatusCode().value()));assertEquals(0,closed.getCount());assertNull(session.getAttribute("deepgramGrantPending"));
 }

 private DeepgramController controller(TeamService teams,DeepgramController.GrantTransport transport){return new DeepgramController(teams,new ObjectMapper(),KEY,transport,Duration.ofSeconds(1));}
 private static HttpResponse<InputStream> response(int status,String body){return response(status,new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
 private static HttpResponse<InputStream> response(int status,InputStream body){return new HttpResponse<>(){public int statusCode(){return status;}public HttpRequest request(){return null;}public Optional<HttpResponse<InputStream>> previousResponse(){return Optional.empty();}public HttpHeaders headers(){return HttpHeaders.of(Map.of(),(a,b)->true);}public InputStream body(){return body;}public Optional<SSLSession> sslSession(){return Optional.empty();}public URI uri(){return URI.create("https://api.deepgram.com/v1/auth/grant");}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}};}
}
