package com.moida.copilot.meeting.api;
import com.moida.copilot.meeting.application.MeetingAskService;
import com.moida.copilot.meeting.application.MeetingAskService.KnowledgeScope;
import com.moida.copilot.meeting.application.MeetingContext;
import com.moida.copilot.transcription.domain.Transcript.Segment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/meetings/{meeting}/ask")
public class MeetingAskController {
  private final MeetingAskService service;
  public MeetingAskController(MeetingAskService service){this.service=service;}
  public record Request(@NotBlank @Size(max=2000) String question,@AssertTrue boolean approved,@Min(0) Integer version,@Size(max=2000) List<@NotNull @Valid Segment> segments,UUID agentId,boolean externalApproved,boolean useTeamDocuments,MeetingContext.Depth responseDepth,KnowledgeScope knowledgeScope){}
  public record ContextRequest(@Min(0) Integer version,@Size(max=2000) List<@NotNull @Valid Segment> segments){}
  @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
  public Object create(Authentication auth,@PathVariable UUID meeting,@Valid @RequestBody Request request){return service.create(meeting,UUID.fromString(auth.getName()),request.question(),request.approved(),request.version(),request.segments(),request.agentId(),request.externalApproved(),request.useTeamDocuments(),request.responseDepth(),request.knowledgeScope());}
  @PostMapping("/context") public Object context(Authentication auth,@PathVariable UUID meeting,@Valid @RequestBody ContextRequest request){return service.previewContext(meeting,UUID.fromString(auth.getName()),request.version(),request.segments());}
  @GetMapping("/{id}") public Object read(Authentication auth,@PathVariable UUID meeting,@PathVariable UUID id){return service.read(meeting,UUID.fromString(auth.getName()),id);}
  @DeleteMapping("/{id}") public Object cancel(Authentication auth,@PathVariable UUID meeting,@PathVariable UUID id){return service.cancel(meeting,UUID.fromString(auth.getName()),id);}
}
