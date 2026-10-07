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
  public List<Meeting> trash(UUID team,UUID user) { teams.requireMember(team,user,false);return meetings.trash(team,user,teams.isOwner(team,user)); }
  public Meeting require(UUID id,UUID user,boolean lock) { Meeting m=meetings.find(id,lock).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"회의를 찾을 수 없습니다."));teams.requireMember(m.teamId(),user,false);return m; }
  @Transactional public Meeting create(UUID team,UUID user,String title) { teams.requireMember(team,user,false);UUID id=UUID.randomUUID();meetings.create(id,team,title.trim(),user);return require(id,user,false); }
  @Transactional public Meeting end(UUID id,UUID user) { require(id,user,true);meetings.end(id);return require(id,user,false); }
  @Transactional public void delete(UUID id,UUID user) { var meeting=require(id,user,true);requireManager(meeting,user);meetings.delete(id,user);meetings.audit(meeting.teamId(),user,"MEETING_TRASHED",id); }
  @Transactional public Meeting restore(UUID id,UUID user) { var meeting=meetings.findDeleted(id,true).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"삭제한 회의를 찾을 수 없습니다."));teams.requireMember(meeting.teamId(),user,false);requireManager(meeting,user);meetings.restore(id);meetings.audit(meeting.teamId(),user,"MEETING_RESTORED",id);return require(id,user,false); }
  private void requireManager(Meeting meeting,UUID user) { if(!user.equals(meeting.createdBy())&&!teams.isOwner(meeting.teamId(),user))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"회의를 만든 사람 또는 팀 소유자만 삭제하거나 복구할 수 있습니다."); }
}
