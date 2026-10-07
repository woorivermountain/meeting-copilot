package com.moida.copilot.transcription.application;
import com.moida.copilot.transcription.domain.Transcript;
import com.moida.copilot.transcription.infrastructure.TranscriptRepository;
import com.moida.copilot.meeting.application.MeetingService;
import com.moida.copilot.meeting.infrastructure.MeetingRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class TranscriptService {
  private final TranscriptRepository transcripts; private final MeetingService access; private final MeetingRepository meetings;
  public TranscriptService(TranscriptRepository transcripts,MeetingService access,MeetingRepository meetings) { this.transcripts=transcripts;this.access=access;this.meetings=meetings; }
  public Transcript read(UUID meeting,UUID user,Integer version) { var m=access.require(meeting,user,false);int selected=version==null?m.revision():version;if(selected<0||selected>m.revision())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"기록 버전을 찾을 수 없습니다.");return new Transcript(selected,transcripts.read(meeting,selected)); }
  public Object history(UUID meeting,UUID user) { access.require(meeting,user,false);return transcripts.history(meeting); }
  @Transactional public Transcript save(UUID meeting,UUID user,int expected,boolean approved,List<Transcript.Segment> segments) {
    if(!approved)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"공유 범위와 원문 저장을 먼저 확인해 주세요.");
    var m=access.require(meeting,user,true);
    if(expected!=m.revision())throw new ResponseStatusException(HttpStatus.CONFLICT,"다른 사용자가 기록을 변경했습니다. 현재 내용을 복사한 뒤 최신 기록을 다시 열어 주세요.");
    if(segments.stream().map(Transcript.Segment::id).distinct().count()!=segments.size())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"중복된 문장 ID입니다.");
    int next=m.revision()+1;transcripts.save(meeting,next,user,segments);meetings.revision(meeting,next);return new Transcript(next,segments);
  }
}
