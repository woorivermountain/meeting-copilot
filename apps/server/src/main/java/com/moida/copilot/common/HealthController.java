package com.moida.copilot.common;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
public class HealthController {
  @GetMapping("/api/health") public Map<String,Object> health() { return Map.of("status","UP","mode","service","transcription","browser","llmPolicy","local-first","llmAvailability","not-probed"); }
}
