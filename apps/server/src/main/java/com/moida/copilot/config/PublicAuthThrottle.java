package com.moida.copilot.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PublicAuthThrottle extends OncePerRequestFilter {
  private final boolean active;
  private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
  private record Window(long minute,int attempts) {}
  public PublicAuthThrottle(@Value("${PUBLIC_DEMO:false}") boolean active) { this.active=active; }

  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
    if(active && "POST".equals(req.getMethod()) && (req.getRequestURI().equals("/api/auth/login") || req.getRequestURI().equals("/api/auth/signup"))) {
      long minute=System.currentTimeMillis()/60000;
      if(windows.size()>=2048) windows.entrySet().removeIf(e->e.getValue().minute()<minute);
      String address=req.getRemoteAddr();
      if((windows.size()>=2048 && !windows.containsKey(address)) || windows.compute(address,(k,w)->new Window(minute,w==null||w.minute()!=minute?1:w.attempts()+1)).attempts()>20) {
        res.setStatus(429);res.setContentType("application/json;charset=UTF-8");res.setHeader("Retry-After","60");
        res.getWriter().write("{\"message\":\"로그인 시도가 많습니다. 잠시 후 다시 시도해 주세요.\"}");return;
      }
    }
    chain.doFilter(req,res);
  }
}
