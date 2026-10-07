package com.moida.copilot.meeting.infrastructure;
import com.moida.copilot.meeting.domain.Meeting;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
public class MeetingRepository {
  private final JdbcClient db;
  public MeetingRepository(JdbcClient db) { this.db=db; }
  public List<Meeting> list(UUID team) { return db.sql("select id,team_id,title,status,revision,created_at from app_meetings where team_id=:team order by created_at desc").param("team",team).query(Meeting.class).list(); }
  public Optional<Meeting> find(UUID id,boolean lock) { return db.sql("select id,team_id,title,status,revision,created_at from app_meetings where id=:id"+(lock?" for update":"")).param("id",id).query(Meeting.class).optional(); }
  public void create(UUID id,UUID team,String title,UUID user) { db.sql("insert into app_meetings(id,team_id,title,created_by,created_at,status,revision) values(:id,:team,:title,:user,current_timestamp,'OPEN',0)").param("id",id).param("team",team).param("title",title).param("user",user).update(); }
  public void end(UUID id) { db.sql("update app_meetings set status='ENDED' where id=:id").param("id",id).update(); }
  public void revision(UUID id,int version) { db.sql("update app_meetings set revision=:version where id=:id").param("version",version).param("id",id).update(); }
}
