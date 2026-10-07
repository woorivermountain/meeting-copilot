package com.moida.copilot.meeting;

import com.moida.copilot.meeting.application.MeetingService;
import com.moida.copilot.meeting.domain.Meeting;
import com.moida.copilot.meeting.infrastructure.MeetingRepository;
import com.moida.copilot.team.application.TeamService;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class MeetingLifecycleTest {
  private final UUID team=UUID.randomUUID(),creator=UUID.randomUUID(),owner=UUID.randomUUID(),member=UUID.randomUUID(),meetingId=UUID.randomUUID();

  @Test void creatorCanTrashAndRestoreWithoutDestroyingMeetingData(){
    var repository=new FakeMeetings(meeting());var service=new MeetingService(repository,access(Set.of(owner)));
    service.delete(meetingId,creator);
    assertTrue(repository.current.isDeleted());
    assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,()->service.require(meetingId,creator,false)).getStatusCode());
    assertEquals("MEETING_TRASHED",repository.audits.getFirst());
    var restored=service.restore(meetingId,creator);
    assertFalse(restored.isDeleted());
    assertEquals(List.of("MEETING_TRASHED","MEETING_RESTORED"),repository.audits);
  }

  @Test void ordinaryMemberCannotDeleteAnotherPersonsMeetingButOwnerCan(){
    var repository=new FakeMeetings(meeting());var service=new MeetingService(repository,access(Set.of(owner)));
    assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,()->service.delete(meetingId,member)).getStatusCode());
    assertFalse(repository.current.isDeleted());
    service.delete(meetingId,owner);
    assertTrue(repository.current.isDeleted());
  }

  @Test void trashListingIsScopedForMembersAndCompleteForOwners(){
    var repository=new FakeMeetings(meeting());var service=new MeetingService(repository,access(Set.of(owner)));
    service.trash(team,member);assertFalse(repository.includeAll);
    service.trash(team,owner);assertTrue(repository.includeAll);
  }

  private Meeting meeting(){return new Meeting(meetingId,team,"합성 회의","ENDED",2,creator,OffsetDateTime.now().minusHours(1),null);}
  private TeamService access(Set<UUID> owners){return new TeamService(null){
    @Override public void requireMember(UUID selected,UUID user,boolean ownerOnly){if(!team.equals(selected)||ownerOnly&&!owners.contains(user))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
    @Override public boolean isOwner(UUID selected,UUID user){return team.equals(selected)&&owners.contains(user);}
  };}

  private static class FakeMeetings extends MeetingRepository {
    Meeting current;boolean includeAll;final List<String> audits=new ArrayList<>();
    FakeMeetings(Meeting current){super(null);this.current=current;}
    @Override public Optional<Meeting> find(UUID id,boolean lock){return current.id().equals(id)&&!current.isDeleted()?Optional.of(current):Optional.empty();}
    @Override public Optional<Meeting> findDeleted(UUID id,boolean lock){return current.id().equals(id)&&current.isDeleted()?Optional.of(current):Optional.empty();}
    @Override public List<Meeting> trash(UUID team,UUID user,boolean includeAll){this.includeAll=includeAll;return List.of();}
    @Override public void delete(UUID id,UUID user){current=new Meeting(current.id(),current.teamId(),current.title(),current.status(),current.revision(),current.createdBy(),current.createdAt(),OffsetDateTime.now());}
    @Override public void restore(UUID id){current=new Meeting(current.id(),current.teamId(),current.title(),current.status(),current.revision(),current.createdBy(),current.createdAt(),null);}
    @Override public void audit(UUID team,UUID actor,String operation,UUID resource){audits.add(operation);}
  }
}
