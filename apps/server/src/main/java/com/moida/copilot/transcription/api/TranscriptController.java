package com.moida.copilot.transcription.api;
import com.moida.copilot.transcription.application.TranscriptService;
import com.moida.copilot.transcription.domain.Transcript;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/meetings/{meeting}/transcript")
public class TranscriptController {
  private final TranscriptService service;
  public TranscriptController(TranscriptService service) { this.service=service; }
  public record Save(@Min(0) int version,boolean approved,@NotNull @Size(max=500) @Valid List<Transcript.Segment> segments) {}
  @GetMapping public Object read(Authentication a,@PathVariable UUID meeting,@RequestParam(required=false) Integer version) { return service.read(meeting,UUID.fromString(a.getName()),version); }
  @PutMapping public Object save(Authentication a,@PathVariable UUID meeting,@Valid @RequestBody Save input) { return service.save(meeting,UUID.fromString(a.getName()),input.version(),input.approved(),input.segments()); }
  @GetMapping("/history") public Object history(Authentication a,@PathVariable UUID meeting) { return service.history(meeting,UUID.fromString(a.getName())); }
}
