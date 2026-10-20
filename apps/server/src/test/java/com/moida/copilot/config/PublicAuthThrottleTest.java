package com.moida.copilot.config;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class PublicAuthThrottleTest {
  @Test void repeatedAuthenticationIsLimitedWithoutBlockingHealthOrOtherClients() throws Exception {
    var filter=new PublicAuthThrottle(true);var accepted=new AtomicInteger();
    for(int i=0;i<21;i++) {
      var req=new MockHttpServletRequest("POST","/api/auth/login");req.setRemoteAddr("192.0.2.1");
      var res=new MockHttpServletResponse();filter.doFilter(req,res,(a,b)->accepted.incrementAndGet());
      assertEquals(i<20?200:429,res.getStatus());
    }
    assertEquals(20,accepted.get());
    var health=new MockHttpServletRequest("GET","/api/health");health.setRemoteAddr("192.0.2.1");
    filter.doFilter(health,new MockHttpServletResponse(),(a,b)->accepted.incrementAndGet());
    var other=new MockHttpServletRequest("POST","/api/auth/login");other.setRemoteAddr("192.0.2.2");
    filter.doFilter(other,new MockHttpServletResponse(),(a,b)->accepted.incrementAndGet());
    assertEquals(22,accepted.get());
  }
}
