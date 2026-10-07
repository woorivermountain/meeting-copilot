package com.moida.copilot.common;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> domain(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "요청을 처리하지 못했습니다." : e.getReason())); }
  @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation() { return ResponseEntity.badRequest().body(Map.of("message", "필수 항목, 형식과 입력 길이를 확인하세요.")); }
  @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("message", "이미 등록된 값이거나 다른 변경과 충돌했습니다.")); }
}
