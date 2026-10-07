package com.moida.copilot.transcription.infrastructure;
import com.moida.copilot.transcription.domain.Transcript;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
public class TranscriptRepository {
  private final JdbcClient db;
  public TranscriptRepository(JdbcClient db) { this.db=db; }
  public List<Transcript.Segment> read(UUID meeting,int version) { return db.sql("select s.segment_id as id,s.received_at,s.text,s.speaker_label from transcript_segments s join transcript_revisions r on r.id=s.revision_id where r.meeting_id=:meeting and r.version=:version order by s.seq").param("meeting",meeting).param("version",version).query(Transcript.Segment.class).list(); }
  public List<Transcript.Revision> history(UUID meeting) { return db.sql("select version,approved_by,created_at from transcript_revisions where meeting_id=:meeting order by version desc").param("meeting",meeting).query(Transcript.Revision.class).list(); }
  public void save(UUID meeting,int version,UUID user,List<Transcript.Segment> segments) {
    UUID revision=UUID.randomUUID();db.sql("insert into transcript_revisions(id,meeting_id,version,approved_by,created_at) values(:id,:meeting,:version,:user,current_timestamp)").param("id",revision).param("meeting",meeting).param("version",version).param("user",user).update();
    for(int i=0;i<segments.size();i++) { var s=segments.get(i);db.sql("insert into transcript_segments(revision_id,segment_id,seq,received_at,text,speaker_label) values(:revision,:id,:seq,:received,:text,:speaker)").param("revision",revision).param("id",s.id()).param("seq",i).param("received",s.receivedAt()).param("text",s.text()).param("speaker",s.speakerLabel(),java.sql.Types.VARCHAR).update(); }
  }
}
