package com.moida.copilot.common;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
public class HealthController {
  // Liveness is not a provider connectivity or model readiness test.
  @GetMapping("/api/health") public Map<String,Object> health() { return Map.of("status","UP","mode","service","providerAvailability","not-probed"); }
}
