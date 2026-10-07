package com.moida.copilot.knowledge.domain;
import java.util.*;
public final class Knowledge {
  private Knowledge(){}
  public enum Sharing { PRIVATE, TEAM_SHARED }
  public record Box(UUID id,String name,String department,UUID createdBy,String sharing){}
  public record CatalogBox(UUID id,String name,String department,String sharing,boolean accessible,boolean owned){}
  public record AccessRequest(UUID id,UUID boxId,String boxName,UUID requesterId,String status,java.time.Instant createdAt){}
  public record Document(UUID id,UUID boxId,String title,String content){}
  public record Agent(UUID id,String name,String department){}
  public record Source(String sourceId,UUID documentId,String title,int start,int end,String quote){}
  public record ToolTrace(String name,String status,int sources){}
  public record Answer(String status,String answer,List<Source> citations,List<ToolTrace> tools,String model){}
}
