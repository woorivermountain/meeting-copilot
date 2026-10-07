package com.moida.copilot.auth.application;
import com.moida.copilot.auth.domain.User;
import com.moida.copilot.auth.infrastructure.UserRepository;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class AuthService {
  private final UserRepository users; private final PasswordEncoder passwords;
  public AuthService(UserRepository users, PasswordEncoder passwords) { this.users=users; this.passwords=passwords; }
  public User signup(String email,String name,String password) {
    if(password.getBytes(StandardCharsets.UTF_8).length>72) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
    User user = new User(UUID.randomUUID(),email.trim().toLowerCase(Locale.ROOT),name.trim(),passwords.encode(password)); users.insert(user); return user;
  }
  public User login(String email,String password) {
    User user=users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
    if(user==null || password.getBytes(StandardCharsets.UTF_8).length>72 || !passwords.matches(password,user.passwordHash())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호를 확인하세요.");
    return user;
  }
  public User.Profile profile(UUID id) { return users.get(id).profile(); }
}
