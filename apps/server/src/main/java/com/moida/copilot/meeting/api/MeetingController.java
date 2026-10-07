package com.moida.copilot.meeting.api;
import com.moida.copilot.meeting.application.MeetingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api")
public class MeetingController {
  private final MeetingService service;
  public MeetingController(MeetingService service) { this.service=service; }
  public record Create(@NotBlank @Size(max=160) String title) {}
  @GetMapping("/teams/{team}/meetings") public Object list(Authentication a,@PathVariable UUID team) { return service.list(team,UUID.fromString(a.getName())); }
  @GetMapping("/teams/{team}/meetings/trash") public Object trash(Authentication a,@PathVariable UUID team) { return service.trash(team,UUID.fromString(a.getName())); }
  @PostMapping("/teams/{team}/meetings") public Object create(Authentication a,@PathVariable UUID team,@Valid @RequestBody Create input) { return service.create(team,UUID.fromString(a.getName()),input.title()); }
  @GetMapping("/meetings/{id}") public Object get(Authentication a,@PathVariable UUID id) { return service.require(id,UUID.fromString(a.getName()),false); }
  @PostMapping("/meetings/{id}/end") public Object end(Authentication a,@PathVariable UUID id) { return service.end(id,UUID.fromString(a.getName())); }
  @DeleteMapping("/meetings/{id}") public Object delete(Authentication a,@PathVariable UUID id) { service.delete(id,UUID.fromString(a.getName()));return Map.of("deleted",true); }
  @PostMapping("/meetings/{id}/restore") public Object restore(Authentication a,@PathVariable UUID id) { return service.restore(id,UUID.fromString(a.getName())); }
}
