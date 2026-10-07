package com.moida.copilot.auth.api;
import com.moida.copilot.auth.application.AuthService;
import com.moida.copilot.auth.domain.User;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth")
public class AuthController {
  private final AuthService service; private final HttpSessionSecurityContextRepository contexts;
  public AuthController(AuthService service,HttpSessionSecurityContextRepository contexts) { this.service=service;this.contexts=contexts; }
  public record Signup(@Email @NotBlank @Size(max=254) String email,@NotBlank @Size(max=80) String name,@Size(min=10,max=72) @NotNull String password) {}
  public record Login(@Email @NotBlank String email,@NotNull @Size(max=72) String password) {}
  @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
  @PostMapping("/signup") public User.Profile signup(@Valid @RequestBody Signup input,HttpServletRequest req,HttpServletResponse res) { return authenticate(service.signup(input.email(),input.name(),input.password()),req,res); }
  @PostMapping("/login") public User.Profile login(@Valid @RequestBody Login input,HttpServletRequest req,HttpServletResponse res) { return authenticate(service.login(input.email(),input.password()),req,res); }
  @GetMapping("/me") public User.Profile me(Authentication auth) { return service.profile(UUID.fromString(auth.getName())); }
  @PostMapping("/logout") public Map<String,Boolean> logout(HttpServletRequest req) { var session=req.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();return Map.of("loggedOut",true); }
  private User.Profile authenticate(User user,HttpServletRequest req,HttpServletResponse res) {
    req.getSession();req.changeSessionId();
    var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(new UsernamePasswordAuthenticationToken(user.id().toString(),null,List.of()));
    SecurityContextHolder.setContext(context);contexts.saveContext(context,req,res);return user.profile();
  }
}
