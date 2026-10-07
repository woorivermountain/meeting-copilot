package com.moida.copilot.knowledge.application;
import com.moida.copilot.knowledge.domain.Knowledge.*;
import com.moida.copilot.knowledge.infrastructure.KnowledgeRepository;
import com.moida.copilot.team.application.TeamService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class KnowledgeService {
  private final KnowledgeRepository repo;private final TeamService teams;
  public KnowledgeService(KnowledgeRepository repo,TeamService teams){this.repo=repo;this.teams=teams;}
  public Object workspace(UUID team,UUID user){teams.requireMember(team,user,false);return Map.of("boxes",repo.boxes(team,user),"documents",repo.documents(team,user),"agents",repo.agents(team),"catalog",repo.catalog(team,user),"accessRequests",repo.requests(team,user));}
  @Transactional public void sharing(UUID team,UUID user,UUID box,Sharing sharing){teams.requireMember(team,user,false);if(!repo.owns(team,box,user))throw denied();repo.sharing(box,sharing);repo.audit(team,user,"BOX_SHARING_"+sharing.name(),box);}
  @Transactional public UUID requestAccess(UUID team,UUID user,UUID box){teams.requireMember(team,user,false);if(repo.catalog(team,user).stream().noneMatch(b->b.id().equals(box)))throw denied();repo.lockBox(team,box);if(repo.allowed(team,box,user))throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 자료 접근 권한이 있습니다.");UUID id=repo.request(box,user);repo.audit(team,user,"ACCESS_REQUEST",box);return id;}
  public List<AccessRequest> requests(UUID team,UUID user){teams.requireMember(team,user,false);return repo.requests(team,user);}
  @Transactional public void decideAccess(UUID team,UUID user,UUID request,boolean approved){teams.requireMember(team,user,false);var r=repo.requests(team,user).stream().filter(v->v.id().equals(request)).findFirst().orElseThrow(KnowledgeService::denied);if(!repo.owns(team,r.boxId(),user))throw denied();repo.lockBox(team,r.boxId());teams.requireMember(team,r.requesterId(),false);if(!repo.decision(request,approved))throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 처리된 접근 요청입니다.");if(approved)repo.grant(r.boxId(),r.requesterId());repo.audit(team,user,approved?"ACCESS_APPROVE":"ACCESS_DENY",r.boxId());}
  @Transactional public UUID box(UUID team,UUID user,String name,String department){teams.requireMember(team,user,false);var id=repo.createBox(team,user,name.trim(),department.trim());repo.audit(team,user,"BOX_CREATE",id);return id;}
  @Transactional public void grant(UUID team,UUID user,UUID box,UUID recipient,boolean allowed){teams.requireMember(team,user,false);teams.requireMember(team,recipient,false);if(!repo.owns(team,box,user))throw denied();if(allowed)repo.grant(box,recipient);else repo.revoke(box,recipient);repo.audit(team,user,allowed?"GRANT":"REVOKE",box);}
  public List<UUID> grants(UUID team,UUID user,UUID box){teams.requireMember(team,user,false);if(!repo.owns(team,box,user))throw denied();return repo.grants(box);}
  @Transactional public UUID document(UUID team,UUID user,UUID box,String title,String content,boolean approved){teams.requireMember(team,user,false);if(!repo.owns(team,box,user))throw denied();if(!approved)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"자료 원문 저장과 공유 범위를 확인해 주세요.");var id=repo.document(box,title.trim(),content.trim(),user);repo.audit(team,user,"DOCUMENT_CREATE",id);return id;}
  @Transactional public UUID agent(UUID team,UUID user,String name,String department,List<UUID> boxes){teams.requireMember(team,user,true);for(UUID box:boxes)if(!repo.allowed(team,box,user))throw denied();var id=repo.agent(team,name.trim(),department.trim(),boxes);repo.audit(team,user,"AGENT_CREATE",id);return id;}
  public Agent requireAgent(UUID team,UUID user,UUID agent){teams.requireMember(team,user,false);return repo.agents(team).stream().filter(a->a.id().equals(agent)).findFirst().orElseThrow(KnowledgeService::denied);}
  // This intersection is re-evaluated on EVERY tool execution, not supplied by the LLM.
  public List<Document> agentDocuments(UUID team,UUID user,UUID agent){requireAgent(team,user,agent);var assigned=repo.agentBoxes(agent);return repo.documents(team,user).stream().filter(d->assigned.contains(d.boxId())).toList();}
  // Personal permission never implies permission to disclose into a shared meeting.
  public List<Document> agentMeetingDocuments(UUID team,UUID user,UUID agent){requireAgent(team,user,agent);var shared=repo.boxes(team,user).stream().filter(b->Sharing.TEAM_SHARED.name().equals(b.sharing())).map(Box::id).collect(java.util.stream.Collectors.toSet());return agentDocuments(team,user,agent).stream().filter(d->shared.contains(d.boxId())).toList();}
  public List<Document> teamMeetingDocuments(UUID team,UUID user){teams.requireMember(team,user,false);var shared=repo.boxes(team,user).stream().filter(b->Sharing.TEAM_SHARED.name().equals(b.sharing())).map(Box::id).collect(java.util.stream.Collectors.toSet());return repo.documents(team,user).stream().filter(d->shared.contains(d.boxId())).toList();}
  public void recheckMeetingSources(UUID team,UUID user,UUID agent,Collection<Source> sources){var allowed=(agent==null?teamMeetingDocuments(team,user):agentMeetingDocuments(team,user,agent)).stream().map(Document::id).collect(java.util.stream.Collectors.toSet());if(sources.stream().anyMatch(s->!allowed.contains(s.documentId())))throw denied();}
  private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"자료 또는 에이전트에 대한 접근 권한이 없습니다.");}
}
