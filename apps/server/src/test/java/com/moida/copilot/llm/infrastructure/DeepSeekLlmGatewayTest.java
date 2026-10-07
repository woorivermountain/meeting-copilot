package com.moida.copilot.llm.infrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeepSeekLlmGatewayTest {
  private final ObjectMapper json=new ObjectMapper();
  private HttpClient client(){var c=mock(HttpClient.class);when(c.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);return c;}
  @Test void consentAndKeyFailClosedBeforeNetwork() throws Exception {var c=client();var gateway=new DeepSeekLlmGateway(json,"deepseek-flash","",c);
    assertEquals(403,assertThrows(ResponseStatusException.class,()->gateway.completeApproved(List.of(),List.of(),false)).getStatusCode().value());
    assertEquals(503,assertThrows(ResponseStatusException.class,()->gateway.completeApproved(List.of(),List.of(),true)).getStatusCode().value());
    assertThrows(ResponseStatusException.class,()->gateway.complete(List.of(),List.of()));assertThrows(IllegalStateException.class,()->gateway.completeLocal(List.of(),List.of()));verify(c,never()).sendAsync(any(),any());
  }
  @Test void fixedOriginNoRedirectAndThinkingDisabled() throws Exception {var c=client();var response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn("{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}".getBytes());when(c.sendAsync(any(),any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(response));
    var gateway=new DeepSeekLlmGateway(json,"deepseek-flash","test-not-secret",c);assertEquals("deepseek",gateway.completeApproved(List.of(),List.of(),true).path("_provider").asText());
    var capture=ArgumentCaptor.forClass(HttpRequest.class);verify(c).sendAsync(capture.capture(),any());var request=capture.getValue();assertEquals("https://api.deepseek.com/chat/completions",request.uri().toString());
    var out=new ByteArrayOutputStream();request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){public void onSubscribe(Flow.Subscription s){s.request(Long.MAX_VALUE);}public void onNext(ByteBuffer b){byte[] bytes=new byte[b.remaining()];b.get(bytes);out.writeBytes(bytes);}public void onError(Throwable t){fail(t);}public void onComplete(){}});
    assertEquals("disabled",json.readTree(out.toByteArray()).path("thinking").path("type").asText());assertEquals("json_object",json.readTree(out.toByteArray()).path("response_format").path("type").asText());
  }
  @Test void providerErrorsAreSanitizedAndNeverRetried() throws Exception {for(int status:List.of(302,401,429,500)){var c=client();var response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn("sensitive provider payload".getBytes());when(c.sendAsync(any(),any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(response));var gateway=new DeepSeekLlmGateway(json,"deepseek-flash","test-key",c);var error=assertThrows(ResponseStatusException.class,()->gateway.completeApproved(List.of(),List.of(),true));assertEquals(502,error.getStatusCode().value());assertFalse(error.getReason().contains("sensitive"));verify(c,times(1)).sendAsync(any(),any());}}
  @Test void redirectCapableTransportIsRejected(){var c=mock(HttpClient.class);when(c.followRedirects()).thenReturn(HttpClient.Redirect.ALWAYS);assertThrows(IllegalArgumentException.class,()->new DeepSeekLlmGateway(json,"model","key",c));}
  @Test void stalledBodyTimesOutAndCancelsTransport(){var c=client();var pending=new java.util.concurrent.CompletableFuture<HttpResponse<Object>>();when(c.sendAsync(any(),any())).thenReturn(pending);var gateway=new DeepSeekLlmGateway(json,"model","key",c,java.time.Duration.ofMillis(20));assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),()->assertEquals(502,assertThrows(ResponseStatusException.class,()->gateway.completeApproved(List.of(),List.of(),true)).getStatusCode().value()));assertTrue(pending.isCancelled());}
  @Test void bodyCapCancelsSubscriptionAndLateSubscriptionAlsoCancelled(){var body=new DeepSeekLlmGateway.LimitedBody();var subscription=mock(Flow.Subscription.class);body.onSubscribe(subscription);body.onNext(List.of(ByteBuffer.allocate(131073)));assertTrue(body.getBody().toCompletableFuture().isCompletedExceptionally());verify(subscription).cancel();var late=new DeepSeekLlmGateway.LimitedBody();late.cancel();var later=mock(Flow.Subscription.class);late.onSubscribe(later);verify(later).cancel();verify(later,never()).request(anyLong());}
  @Test void bodyCompletesOnlyAfterFinalChunk(){var body=new DeepSeekLlmGateway.LimitedBody();body.onSubscribe(mock(Flow.Subscription.class));body.onNext(List.of(ByteBuffer.wrap("ok".getBytes())));assertFalse(body.getBody().toCompletableFuture().isDone());body.onComplete();assertArrayEquals("ok".getBytes(),body.getBody().toCompletableFuture().join());}
}
