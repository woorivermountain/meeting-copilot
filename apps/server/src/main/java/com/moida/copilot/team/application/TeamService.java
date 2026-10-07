package com.moida.copilot.team.application;
import com.moida.copilot.team.domain.Team;
import com.moida.copilot.team.infrastructure.TeamRepository;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class TeamService {
  private final TeamRepository teams;
  public TeamService(TeamRepository teams) { this.teams=teams; }
  public List<Team> list(UUID user) { return teams.list(user); }
  public void requireMember(UUID team,UUID user,boolean ownerOnly) { var role=teams.role(team,user);if(role.isEmpty() || ownerOnly && !role.get().equals("OWNER")) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"해당 팀에 대한 권한이 없습니다."); }
  public boolean isOwner(UUID team,UUID user) { return teams.role(team,user).filter("OWNER"::equals).isPresent(); }
  public List<TeamRepository.Member> members(UUID team,UUID user) { requireMember(team,user,false);return teams.members(team); }
  @Transactional public Created create(UUID user,String name) { byte[] random=new byte[12];new SecureRandom().nextBytes(random);String code=HexFormat.of().formatHex(random);UUID id=UUID.randomUUID();teams.create(id,name.trim(),user,hash(code));return new Created(id,name.trim(),code); }
  @Transactional public UUID join(UUID user,String code) { UUID id=teams.byCode(hash(code.trim())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"초대 코드를 확인하세요."));if(teams.role(id,user).isEmpty())teams.addMember(id,user,"MEMBER");return id; }
  private String hash(String text) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
  public record Created(UUID id,String name,String inviteCode) {}
}
