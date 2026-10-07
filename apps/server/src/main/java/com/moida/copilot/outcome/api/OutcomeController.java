package com.moida.copilot.outcome.api;
import com.moida.copilot.outcome.application.OutcomeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/meetings/{meeting}/outcomes")
public class OutcomeController {
  private final OutcomeService service;public OutcomeController(OutcomeService service){this.service=service;}
  public record Create(@NotNull @Pattern(regexp="DECISION|ACTION|ISSUE") String kind,@NotBlank @Size(max=2000) String text,UUID ownerId,LocalDate dueDate,boolean approved){}
  public record Update(@NotNull @Pattern(regexp="TODO|DOING|DONE") String status,@Min(0) int version){}
  @GetMapping public Object list(Authentication a,@PathVariable UUID meeting){return service.list(meeting,UUID.fromString(a.getName()));}
  @PostMapping public Object create(Authentication a,@PathVariable UUID meeting,@Valid @RequestBody Create in){return service.create(meeting,UUID.fromString(a.getName()),in.kind(),in.text(),in.ownerId(),in.dueDate(),in.approved());}
  @PatchMapping("/{id}") public Object status(Authentication a,@PathVariable UUID meeting,@PathVariable UUID id,@Valid @RequestBody Update in){return service.status(meeting,UUID.fromString(a.getName()),id,in.status(),in.version());}
}
