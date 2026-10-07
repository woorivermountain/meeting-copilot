package com.moida.copilot.team.api;
import com.moida.copilot.team.application.TeamService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/teams")
public class TeamController {
  private final TeamService service;
  public TeamController(TeamService service) { this.service=service; }
  public record Create(@NotBlank @Size(max=80) String name) {}
  public record Join(@NotBlank @Size(min=24,max=24) String code) {}
  @GetMapping public Object list(Authentication a) { return service.list(UUID.fromString(a.getName())); }
  @PostMapping public Object create(Authentication a,@Valid @RequestBody Create input) { return service.create(UUID.fromString(a.getName()),input.name()); }
  @PostMapping("/join") public Object join(Authentication a,@Valid @RequestBody Join input) { return java.util.Map.of("id",service.join(UUID.fromString(a.getName()),input.code())); }
  @GetMapping("/{team}/members") public Object members(Authentication a,@PathVariable UUID team) { return service.members(team,UUID.fromString(a.getName())); }
}
