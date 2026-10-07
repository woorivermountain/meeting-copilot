package com.moida.copilot.outcome.application;
import com.moida.copilot.outcome.infrastructure.OutcomeRepository;
import com.moida.copilot.meeting.application.MeetingService;
import com.moida.copilot.team.application.TeamService;
import java.util.*;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class OutcomeService {
  private final OutcomeRepository outcomes;private final MeetingService meetings;private final TeamService teams;
  public OutcomeService(OutcomeRepository outcomes,MeetingService meetings,TeamService teams) { this.outcomes=outcomes;this.meetings=meetings;this.teams=teams; }
  public Object list(UUID meeting,UUID user) { meetings.require(meeting,user,false);return outcomes.list(meeting); }
  @Transactional public Object create(UUID meeting,UUID user,String kind,String text,UUID owner,LocalDate due,boolean approved) { var m=meetings.require(meeting,user,false);if(!approved)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"검토 확인이 필요합니다.");if(owner!=null)teams.requireMember(m.teamId(),owner,false);if(!kind.equals("ACTION")&&(owner!=null||due!=null))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"담당자와 기한은 할 일에만 지정합니다.");return Map.of("id",outcomes.create(meeting,kind,text.trim(),owner,due,user)); }
  @Transactional public Object status(UUID meeting,UUID user,UUID id,String status,int version) { meetings.require(meeting,user,false);if(outcomes.status(meeting,id,status,version)!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"최신 할 일을 다시 불러온 뒤 변경해 주세요.");return outcomes.list(meeting); }
}
