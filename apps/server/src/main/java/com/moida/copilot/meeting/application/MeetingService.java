package com.moida.copilot.meeting.application;
import com.moida.copilot.meeting.domain.Meeting;
import com.moida.copilot.meeting.infrastructure.MeetingRepository;
import com.moida.copilot.team.application.TeamService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class MeetingService {
  private final MeetingRepository meetings; private final TeamService teams;
  public MeetingService(MeetingRepository meetings,TeamService teams) { this.meetings=meetings;this.teams=teams; }
  public List<Meeting> list(UUID team,UUID user) { teams.requireMember(team,user,false);return meetings.list(team); }
  public Meeting require(UUID id,UUID user,boolean lock) { Meeting m=meetings.find(id,lock).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"회의를 찾을 수 없습니다."));teams.requireMember(m.teamId(),user,false);return m; }
  @Transactional public Meeting create(UUID team,UUID user,String title) { teams.requireMember(team,user,false);UUID id=UUID.randomUUID();meetings.create(id,team,title.trim(),user);return require(id,user,false); }
  @Transactional public Meeting end(UUID id,UUID user) { require(id,user,true);meetings.end(id);return require(id,user,false); }
}
