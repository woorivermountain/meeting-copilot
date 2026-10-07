package com.moida.copilot.transcription.domain;
import java.util.*;
import java.time.OffsetDateTime;
import jakarta.validation.constraints.*;
public record Transcript(int version,List<Segment> segments) {
  public record Segment(@NotNull UUID id,@NotNull OffsetDateTime receivedAt,@NotBlank @Size(max=4000) String text,@Size(max=80) String speakerLabel) {
    public Segment(UUID id,OffsetDateTime receivedAt,String text){this(id,receivedAt,text,null);}
  }
  public record Revision(int version,UUID approvedBy,OffsetDateTime createdAt) {}
}
