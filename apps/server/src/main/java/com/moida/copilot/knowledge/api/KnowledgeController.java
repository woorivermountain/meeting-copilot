package com.moida.copilot.knowledge.api;
import com.moida.copilot.knowledge.application.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/teams/{team}/knowledge")
public class KnowledgeController {
  private final KnowledgeService service;private final AgentRunner runner;
  public KnowledgeController(KnowledgeService service,AgentRunner runner){this.service=service;this.runner=runner;}
  public record Box(@NotBlank @Size(max=80) String name,@NotBlank @Size(max=80) String department){}
  public record Document(@NotNull UUID boxId,@NotBlank @Size(max=160) String title,@NotBlank @Size(max=20000) String content,@AssertTrue boolean approved){}
  public record Agent(@NotBlank @Size(max=80) String name,@NotBlank @Size(max=80) String department,@NotEmpty @Size(max=20) List<@NotNull UUID> boxIds){}
  public record Grant(@NotNull UUID userId,boolean allowed){}
  public record Sharing(@NotNull com.moida.copilot.knowledge.domain.Knowledge.Sharing sharing){}
  public record Decision(boolean approved){}
  public record Question(@NotBlank @Size(max=2000) String question,@AssertTrue boolean approved,boolean externalApproved){}
  private UUID user(Authentication a){return UUID.fromString(a.getName());}
  @GetMapping Object workspace(Authentication a,@PathVariable UUID team){return service.workspace(team,user(a));}
  @PostMapping("/boxes") Object box(Authentication a,@PathVariable UUID team,@Valid @RequestBody Box b){return Map.of("id",service.box(team,user(a),b.name(),b.department()));}
  @PostMapping("/documents") Object document(Authentication a,@PathVariable UUID team,@Valid @RequestBody Document d){return Map.of("id",service.document(team,user(a),d.boxId(),d.title(),d.content(),d.approved()));}
  @PostMapping("/agents") Object agent(Authentication a,@PathVariable UUID team,@Valid @RequestBody Agent d){return Map.of("id",service.agent(team,user(a),d.name(),d.department(),d.boxIds()));}
  @GetMapping("/boxes/{box}/grants") Object grants(Authentication a,@PathVariable UUID team,@PathVariable UUID box){return service.grants(team,user(a),box);}
  @PutMapping("/boxes/{box}/grants") Object grant(Authentication a,@PathVariable UUID team,@PathVariable UUID box,@Valid @RequestBody Grant g){service.grant(team,user(a),box,g.userId(),g.allowed());return Map.of("updated",true);}
  @PutMapping("/boxes/{box}/sharing") Object sharing(Authentication a,@PathVariable UUID team,@PathVariable UUID box,@Valid @RequestBody Sharing s){service.sharing(team,user(a),box,s.sharing());return Map.of("updated",true);}
  @PostMapping("/boxes/{box}/access-requests") Object requestAccess(Authentication a,@PathVariable UUID team,@PathVariable UUID box){return Map.of("id",service.requestAccess(team,user(a),box));}
  @GetMapping("/access-requests") Object requests(Authentication a,@PathVariable UUID team){return service.requests(team,user(a));}
  @PutMapping("/access-requests/{request}/decision") Object decision(Authentication a,@PathVariable UUID team,@PathVariable UUID request,@Valid @RequestBody Decision d){service.decideAccess(team,user(a),request,d.approved());return Map.of("updated",true);}
  @PostMapping("/agents/{agent}/ask") Object ask(Authentication a,@PathVariable UUID team,@PathVariable UUID agent,@Valid @RequestBody Question q){return runner.ask(team,user(a),agent,q.question(),q.externalApproved());}
}
