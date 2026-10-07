package com.moida.copilot.outcome.infrastructure;
import com.moida.copilot.outcome.domain.Outcome;
import java.util.*;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
public class OutcomeRepository {
  private final JdbcClient db;
  public OutcomeRepository(JdbcClient db) { this.db=db; }
  public List<Outcome> list(UUID meeting) { return db.sql("select id,kind,text,owner_id,due_date,status,version from meeting_outcomes where meeting_id=:meeting order by created_at").param("meeting",meeting).query(Outcome.class).list(); }
  public UUID create(UUID meeting,String kind,String text,UUID owner,LocalDate due,UUID user) { UUID id=UUID.randomUUID();db.sql("insert into meeting_outcomes(id,meeting_id,kind,text,owner_id,due_date,status,version,approved_by,created_at) values(:id,:meeting,:kind,:text,:owner,:due,'TODO',0,:user,current_timestamp)").param("id",id).param("meeting",meeting).param("kind",kind).param("text",text).param("owner",owner).param("due",due).param("user",user).update();return id; }
  public int status(UUID meeting,UUID id,String status,int expected) { return db.sql("update meeting_outcomes set status=:status,version=version+1 where id=:id and meeting_id=:meeting and version=:version and kind='ACTION'").param("status",status).param("id",id).param("meeting",meeting).param("version",expected).update(); }
}
