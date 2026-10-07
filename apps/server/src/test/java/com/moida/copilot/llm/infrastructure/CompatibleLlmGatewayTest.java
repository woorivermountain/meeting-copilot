package com.moida.copilot.llm.infrastructure;

import java.io.*;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompatibleLlmGatewayTest {
  @Test void closesAStalledResponseBodyAtTheTotalDeadline()throws Exception{
    var closed=new CountDownLatch(1);var input=new InputStream(){private volatile boolean done;@Override public int read()throws IOException{try{while(!done)Thread.sleep(10);return -1;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}@Override public void close(){done=true;closed.countDown();}};
    assertTimeoutPreemptively(Duration.ofSeconds(1),()->assertThrows(HttpTimeoutException.class,()->CompatibleLlmGateway.readResponse(input,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(40))));
    assertTrue(closed.await(1,TimeUnit.SECONDS));
  }
}
