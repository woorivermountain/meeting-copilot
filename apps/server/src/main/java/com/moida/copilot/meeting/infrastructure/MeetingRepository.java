package com.moida.copilot.meeting.infrastructure;
import com.moida.copilot.meeting.domain.Meeting;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
public class MeetingRepository {
  private final JdbcClient db;
  private static final String COLUMNS="id,team_id,title,status,revision,created_by,created_at,deleted_at";
  public MeetingRepository(JdbcClient db) { this.db=db; }
  public List<Meeting> list(UUID team) { return db.sql("select "+COLUMNS+" from app_meetings where team_id=:team and deleted_at is null order by created_at desc").param("team",team).query(Meeting.class).list(); }
  public List<Meeting> trash(UUID team,UUID user,boolean includeAll) {
    if(includeAll)return db.sql("select "+COLUMNS+" from app_meetings where team_id=:team and deleted_at is not null order by deleted_at desc").param("team",team).query(Meeting.class).list();
    return db.sql("select "+COLUMNS+" from app_meetings where team_id=:team and deleted_at is not null and (created_by=:user or deleted_by=:user) order by deleted_at desc").param("team",team).param("user",user).query(Meeting.class).list();
  }
  public Optional<Meeting> find(UUID id,boolean lock) { return db.sql("select "+COLUMNS+" from app_meetings where id=:id and deleted_at is null"+(lock?" for update":"")).param("id",id).query(Meeting.class).optional(); }
  public Optional<Meeting> findDeleted(UUID id,boolean lock) { return db.sql("select "+COLUMNS+" from app_meetings where id=:id and deleted_at is not null"+(lock?" for update":"")).param("id",id).query(Meeting.class).optional(); }
  public void create(UUID id,UUID team,String title,UUID user) { db.sql("insert into app_meetings(id,team_id,title,created_by,created_at,status,revision) values(:id,:team,:title,:user,current_timestamp,'OPEN',0)").param("id",id).param("team",team).param("title",title).param("user",user).update(); }
  public void end(UUID id) { db.sql("update app_meetings set status='ENDED' where id=:id").param("id",id).update(); }
  public void revision(UUID id,int version) { db.sql("update app_meetings set revision=:version where id=:id").param("version",version).param("id",id).update(); }
  public void delete(UUID id,UUID user) { db.sql("update app_meetings set deleted_at=current_timestamp,deleted_by=:user where id=:id and deleted_at is null").param("id",id).param("user",user).update(); }
  public void restore(UUID id) { db.sql("update app_meetings set deleted_at=null,deleted_by=null where id=:id and deleted_at is not null").param("id",id).update(); }
  public void audit(UUID team,UUID actor,String operation,UUID resource) { db.sql("insert into app_audit(id,team_id,actor_id,operation,resource_id,created_at) values(:id,:team,:actor,:operation,:resource,current_timestamp)").param("id",UUID.randomUUID()).param("team",team).param("actor",actor).param("operation",operation).param("resource",resource).update(); }
}
