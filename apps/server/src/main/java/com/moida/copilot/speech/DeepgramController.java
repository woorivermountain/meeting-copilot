package com.moida.copilot.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moida.copilot.team.application.TeamService;
import jakarta.servlet.http.HttpSession;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Opt-in pilot: short-lived credentials, never exposes the permanent key.
 * A granted token is not team-scoped by Deepgram. Public multi-tenant rollout
 * requires a server relay and account-wide quotas/revocation enforcement.
 */
@RestController @RequestMapping("/api/teams/{team}/speech/deepgram")
public class DeepgramController {
  private final TeamService teams;private final ObjectMapper json;private final String key;
  private final GrantTransport transport;private final Duration grantTimeout;
  @FunctionalInterface interface GrantTransport { HttpResponse<InputStream> send(HttpRequest request)throws IOException,InterruptedException; }
  @Autowired
  public DeepgramController(TeamService teams,ObjectMapper json,@Value("${DEEPGRAM_API_KEY:}") String key){this(teams,json,key,defaultTransport(),Duration.ofSeconds(10));}
  DeepgramController(TeamService teams,ObjectMapper json,String key,GrantTransport transport,Duration grantTimeout){this.teams=teams;this.json=json;this.key=key;this.transport=transport;this.grantTimeout=grantTimeout;}
  private static GrantTransport defaultTransport(){var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();return request->http.send(request,HttpResponse.BodyHandlers.ofInputStream());}
  private boolean ready(){return key.matches("[A-Za-z0-9_-]{16,256}");}
  @GetMapping("/status") public Object status(Authentication auth,@PathVariable UUID team){teams.requireMember(team,UUID.fromString(auth.getName()),false);return Map.of("ready",ready(),"message",ready()?"Deepgram 설정이 준비됐어요. 실제 연결과 혼합 발화 정확도는 테스트가 필요해요.":"서버에 DEEPGRAM_API_KEY 설정이 필요해요.");}
  public record Approval(boolean externalApproved){}
  @PostMapping("/token") public ResponseEntity<?> token(Authentication auth,@PathVariable UUID team,@RequestBody Approval approval,HttpSession session){
    var user=UUID.fromString(auth.getName());teams.requireMember(team,user,false);
    if(!approval.externalApproved())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Deepgram 음성 전송 동의가 필요해요.");
    if(!ready())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Deepgram API 키가 설정되지 않았어요.");
    synchronized(session){
      Long last=(Long)session.getAttribute("deepgramGrantAt");
      long now=System.currentTimeMillis();
      if(Boolean.TRUE.equals(session.getAttribute("deepgramGrantPending"))||last!=null&&now-last<30000)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"30초 후 다시 연결해 주세요.");
      session.setAttribute("deepgramGrantPending",true);
    }
    try{
      long deadline=System.nanoTime()+grantTimeout.toNanos();
      var req=HttpRequest.newBuilder(URI.create("https://api.deepgram.com/v1/auth/grant")).timeout(grantTimeout).header("Authorization","Token "+key).POST(HttpRequest.BodyPublishers.noBody()).build();
      var response=transport.send(req);
      try(var input=response.body()){
        if(response.statusCode()!=200)throw new IllegalStateException();
        var reading=CompletableFuture.supplyAsync(()->{try{return input.readNBytes(16385);}catch(Exception e){throw new CompletionException(e);}});
        byte[] bytes;try{bytes=reading.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);}finally{if(!reading.isDone())reading.cancel(true);}
        if(bytes.length>16384)throw new IllegalStateException();var data=json.readTree(bytes);String token=data.path("access_token").asText();
        if(!token.matches("[A-Za-z0-9_.-]{20,16000}"))throw new IllegalStateException();
        teams.requireMember(team,user,false);
        // Only successful grants consume the per-session retry window. A failed
        // provider request must remain recoverable without making the user wait.
        synchronized(session){session.setAttribute("deepgramGrantAt",System.currentTimeMillis());}
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("accessToken",token));
      }
    }catch(ResponseStatusException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Deepgram 연결이 중단됐어요.");}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Deepgram 연결 권한을 받지 못했어요. 키의 Member 권한과 사용 한도를 확인해 주세요.");}
    finally{synchronized(session){session.removeAttribute("deepgramGrantPending");}}
  }
}
