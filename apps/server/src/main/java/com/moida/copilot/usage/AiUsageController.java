package com.moida.copilot.usage;

import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/teams/{team}/usage")
public class AiUsageController {
  private final AiUsageService usage;
  public AiUsageController(AiUsageService usage){this.usage=usage;}

  @GetMapping
  public Object view(
      Authentication auth,
      @PathVariable UUID team,
      @RequestParam(defaultValue="30") @Min(1) @Max(90) int days){
    return usage.view(team,UUID.fromString(auth.getName()),days);
  }
}
