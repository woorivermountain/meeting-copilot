package com.moida.copilot.team.infrastructure;
import com.moida.copilot.team.domain.Team;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
public class TeamRepository {
  private final JdbcClient db;
  public TeamRepository(JdbcClient db) { this.db=db; }
  public List<Team> list(UUID user) { return db.sql("select t.id,t.name,m.role from app_teams t join app_team_members m on m.team_id=t.id where m.user_id=:user order by t.created_at").param("user",user).query(Team.class).list(); }
  public Optional<String> role(UUID team,UUID user) { return db.sql("select role from app_team_members where team_id=:team and user_id=:user").param("team",team).param("user",user).query(String.class).optional(); }
  public void create(UUID id,String name,UUID owner,String hash) { db.sql("insert into app_teams(id,name,owner_id,invite_hash,created_at) values(:id,:name,:owner,:hash,current_timestamp)").param("id",id).param("name",name).param("owner",owner).param("hash",hash).update();addMember(id,owner,"OWNER"); }
  public void addMember(UUID team,UUID user,String role) { db.sql("insert into app_team_members(team_id,user_id,role) values(:team,:user,:role)").param("team",team).param("user",user).param("role",role).update(); }
  public Optional<UUID> byCode(String hash) { return db.sql("select id from app_teams where invite_hash=:hash").param("hash",hash).query(UUID.class).optional(); }
  public List<Member> members(UUID team) { return db.sql("select u.id,u.name,u.email,m.role from app_users u join app_team_members m on m.user_id=u.id where m.team_id=:team order by u.name").param("team",team).query(Member.class).list(); }
  public record Member(UUID id,String name,String email,String role) {}
}
