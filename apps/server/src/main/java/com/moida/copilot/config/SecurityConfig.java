package com.moida.copilot.config;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
@Configuration
public class SecurityConfig {
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
  @Bean HttpSessionSecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
  @Bean SecurityFilterChain security(HttpSecurity http, HttpSessionSecurityContextRepository repository) throws Exception {
    return http.authorizeHttpRequests(a -> a.requestMatchers("/api/auth/csrf", "/api/auth/signup", "/api/auth/login", "/api/health").permitAll().anyRequest().authenticated())
      .securityContext(c -> c.securityContextRepository(repository))
      .requestCache(c -> c.disable()).formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
      .exceptionHandling(c -> c.authenticationEntryPoint((req,res,e) -> { res.setStatus(401); res.setContentType("application/json;charset=UTF-8"); res.getWriter().write("{\"message\":\"로그인이 필요합니다.\"}"); })
        .accessDeniedHandler((req,res,e) -> { res.setStatus(403); res.setContentType("application/json;charset=UTF-8"); res.getWriter().write("{\"message\":\"권한 또는 보안 토큰을 확인하세요. 새로고침 후 다시 시도하세요.\"}"); }))
      .build();
  }
}
