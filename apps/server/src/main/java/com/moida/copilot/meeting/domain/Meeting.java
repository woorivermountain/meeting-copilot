package com.moida.copilot.meeting.domain;
import java.util.UUID;
import java.time.OffsetDateTime;
public record Meeting(UUID id,UUID teamId,String title,String status,int revision,UUID createdBy,OffsetDateTime createdAt,OffsetDateTime deletedAt) {
  public Meeting(UUID id,UUID teamId,String title,String status,int revision,OffsetDateTime createdAt) { this(id,teamId,title,status,revision,null,createdAt,null); }
  public boolean isOpen() { return status.equals("OPEN"); }
  public boolean isDeleted() { return deletedAt!=null; }
}
